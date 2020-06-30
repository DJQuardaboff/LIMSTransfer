package com.porterlee.transfer;

import android.database.Cursor;
import android.database.DataSetObserver;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class SelectableCursorRecyclerViewAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
    private volatile Cursor mCursor;
    private String mIdColumnName;
    private boolean mDataValid;
    private int mRowIdColumn;
    private DataSetObserver mDataSetObserver;
    private HashMap<String, Integer> mBarcodeToIndexMap = new HashMap<>();
    private ArrayList<String> mDuplicateBarcodes = new ArrayList<>();
    private int mSelectedItem = -1;
    private boolean mIsCanceled = false;

    public SelectableCursorRecyclerViewAdapter(Cursor cursor, @NonNull String idColumnName) {
        mCursor = cursor;
        mIdColumnName = idColumnName;
        mDataValid = cursor != null;
        mRowIdColumn = mDataValid ? mCursor.getColumnIndex(mIdColumnName) : -1;
        mDataSetObserver = new NotifyingDataSetObserver();
        if (mCursor != null) {
            mCursor.registerDataSetObserver(mDataSetObserver);
        }
    }

    public Cursor getCursor() {
        return mCursor;
    }

    @Override
    public int getItemCount() {
        if (mDataValid && mCursor != null) {
            return mCursor.getCount();
        }
        return 0;
    }

    @Override
    public long getItemId(int position) {
        if (mDataValid && mCursor != null && mCursor.moveToPosition(position)) {
            return mCursor.getLong(mRowIdColumn);
        }
        return 0;
    }

    @Override
    public void setHasStableIds(boolean hasStableIds) {
        super.setHasStableIds(true);
    }

    public abstract void onBindViewHolder(VH viewHolder, Cursor cursor);

    @Override
    public void onBindViewHolder(@NonNull VH viewHolder, int position) {
        if (!mDataValid) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        if (!mCursor.moveToPosition(position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }
        onBindViewHolder(viewHolder, mCursor);
    }

    /**
     * Change the underlying cursor to a new cursor. If there is an existing cursor it will be
     * closed.
     */
    public void changeCursor(Cursor cursor) {
        Cursor old = swapCursor(cursor);
        if (old != null && !old.isClosed()) {
            old.close();
        }
    }

    /**
     * Swap in a new Cursor, returning the old Cursor.  Unlike
     * {@link #changeCursor(Cursor)}, the returned old Cursor is <em>not</em>
     * closed.
     */
    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor == mCursor) {
            return null;
        }
        final Cursor oldCursor = mCursor;
        if (oldCursor != null && mDataSetObserver != null) {
            oldCursor.unregisterDataSetObserver(mDataSetObserver);
        }
        mCursor = newCursor;
        if (mCursor != null) {
            if (mDataSetObserver != null) {
                mCursor.registerDataSetObserver(mDataSetObserver);
            }
            mRowIdColumn = newCursor.getColumnIndexOrThrow(mIdColumnName);
            mDataValid = true;
            //
            mBarcodeToIndexMap.clear();
            mDuplicateBarcodes.clear();
            final int barcodeColumnIndex = mCursor.getColumnIndex(TransferDatabase.Key.BARCODE);
            if (barcodeColumnIndex != -1) {
                mCursor.moveToPosition(-1);
                while (mCursor.moveToNext()) {
                    String barcode = mCursor.getString(barcodeColumnIndex);
                    if (mBarcodeToIndexMap.containsKey(barcode)) {
                        mDuplicateBarcodes.add(barcode);
                    }
                    mBarcodeToIndexMap.put(barcode, mCursor.getPosition());
                }
            }
            //
            notifyDataSetChanged();
        } else {
            mRowIdColumn = -1;
            mDataValid = false;
            notifyDataSetChanged();
            //There is no notifyDataSetInvalidated() method in RecyclerView.Adapter
        }
        return oldCursor;
    }

    public boolean getIsDuplicate(String barcode) {
        return mDuplicateBarcodes.contains(barcode);
    }

    public int getIndexOfBarcode(String barcode) {
        Integer index = mBarcodeToIndexMap.get(barcode);
        return index != null ? index : -1;
    }

    public boolean setSelectedItem(int index) {
        if (index >= getItemCount() || index == mSelectedItem)
            return false;

        notifyItemChanged(mSelectedItem);
        mSelectedItem = index;
        notifyItemChanged(mSelectedItem);
        return true;
    }

    public int getSelectedItem() {
        return mSelectedItem;
    }

    public boolean getIsCanceled() {
        return mIsCanceled;
    }

    public void setIsCanceled(boolean isCanceled) {
        mIsCanceled = isCanceled;
    }

    public int getRowIndexByColumnData(String columnName, String columnData) {
        int position = -1;
        mCursor.moveToFirst();
        int columnIndex = mCursor.getColumnIndex(columnName);

        while (!mCursor.isAfterLast()) {
            if (mCursor.getString(columnIndex).equals(columnData)) {
                position = mCursor.getPosition();
                break;
            }
            mCursor.moveToNext();
        }

        return position;
    }

    private class NotifyingDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            super.onChanged();
            mDataValid = true;
            notifyDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
            mDataValid = false;
            notifyDataSetChanged();
            //There is no notifyDataSetInvalidated() method in RecyclerView.Adapter
        }
    }
}