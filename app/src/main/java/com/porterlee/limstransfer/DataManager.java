package com.porterlee.limstransfer;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.porterlee.limstransfer.Scanner.JanamScanner;
import com.porterlee.limstransfer.Scanner.Scanner;
import com.porterlee.limstransfer.Scanner.ZebraScanner;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.regex.Pattern;

public class DataManager {
    public static final String TAG = TransferActivity.class.getName();
    public static final File EXTERNAL_PATH = new File(Environment.getExternalStorageDirectory(), "Transfer");
    public static final File SIGNATURES_PATH = new File(EXTERNAL_PATH, "Signatures");
    public static final File OUTPUT_FILE = new File(EXTERNAL_PATH, "transfer.txt");
    public static final String SIGNATURE_FILE_NAME = "signature_%d.png";
    private volatile TransferDatabase mTransferDatabase;
    private Scanner mScanner;
    private Transfer mCurrentTransfer;
    private Runnable mOnCurrentTransferChangedListener;
    private boolean mIsShowingDialog;

    public DataManager() { }

    public boolean initScanner(Scanner.OnBarcodeScannedListener onBarcodeScannedListener) {

        if (JanamScanner.isCompatible() && (mScanner = new JanamScanner()).init()) {
            mScanner.setOnBarcodeScannedListener(onBarcodeScannedListener);
            return true;
        }/* else if (ZebraScanner.isCompatible() && (mScanner = new ZebraScanner()).init()) {
            mScanner.setOnBarcodeScannedListener(onBarcodeScannedListener);
            return true;
        }*/

        Log.w(TAG, "Auto-detecting scanner SDK");

        if ((mScanner = new JanamScanner()).init()) {
            mScanner.setOnBarcodeScannedListener(onBarcodeScannedListener);
            return true;
        }/* else if ((mScanner = new ZebraScanner()).init()) {
            mScanner.setOnBarcodeScannedListener(onBarcodeScannedListener);
            return true;
        }*/

        return false;
    }

    public void init(Context context) {
        mTransferDatabase = new TransferDatabase(context);
        mCurrentTransfer = getLastNotFinalizedNotCanceledTransfer();

        if (mOnCurrentTransferChangedListener != null)
            mOnCurrentTransferChangedListener.run();

        if (!EXTERNAL_PATH.mkdirs() && !EXTERNAL_PATH.exists())
            Log.w(TAG, "External directory does not exist and could not be created, this may cause a problem");

        if (!SIGNATURES_PATH.mkdirs() && !SIGNATURES_PATH.exists())
            Log.w(TAG, "Signature directory does not exist and could not be created, this may cause a problem");
    }

    public Scanner getScanner() {
        return mScanner;
    }

    public Runnable getOnCurrentTransferChangedListener() {
        return mOnCurrentTransferChangedListener;
    }

    public void setOnCurrentTransferChangedListener(Runnable onCurrentTransferChangedListener) {
        this.mOnCurrentTransferChangedListener = onCurrentTransferChangedListener;
    }

    private Utils.QueryHolder getCurrentTransferRowQuery() {
        return new Utils.QueryHolder(mTransferDatabase.getDatabase(), "SELECT " + TransferDatabase.Key.ID + ", " + TransferDatabase.Key.LOCATION_BARCODE + ", " + TransferDatabase.Key.START_DATE_TIME + " FROM " + TransferDatabase.TransferTable.NAME + " WHERE " + TransferDatabase.Key.ID + " = ?", String.valueOf(getTransferId()));
    }

    private Utils.QueryHolder getItemsWithCurrentTransferIdQuery() {
        return new Utils.QueryHolder(mTransferDatabase.getDatabase(), "SELECT " + TransferDatabase.Key.BARCODE + ", " + TransferDatabase.Key.DATE_TIME + " FROM " + TransferDatabase.ItemTable.NAME + " WHERE " + TransferDatabase.Key.TRANSFER_ID + " = ? ORDER BY " + TransferDatabase.Key.ID, String.valueOf(getTransferId()));
    }

    private Transfer getLastNotFinalizedNotCanceledTransfer() {
        final Cursor cursor = mTransferDatabase.getDatabase().query(TransferDatabase.TransferTable.NAME, new String[] { TransferDatabase.Key.ID, TransferDatabase.Key.SIGNED, TransferDatabase.Key.FINALIZED, TransferDatabase.Key.CANCELED, TransferDatabase.Key.LOCATION_BARCODE }, TransferDatabase.Key.FINALIZED + " = \'0\' AND " + TransferDatabase.Key.CANCELED + " = \'0\'", null, null, null, TransferDatabase.Key.ID + " DESC", "1");
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        Transfer temp = new Transfer(cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.ID)), cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.SIGNED)) != 0, cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.FINALIZED)) != 0, cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.CANCELED)) != 0, cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.LOCATION_BARCODE)));
        cursor.close();
        return temp;
    }

    public Transfer getCurrentTransfer() {
        return mCurrentTransfer;
    }

    public boolean finalizeCurrentTransfer() {
        final boolean success = mTransferDatabase.update_transferTable_set_finalized_equalTo_where_id_equals(true, getTransferId()) > 0;
        if (success) {
            mCurrentTransfer = getLastNotFinalizedNotCanceledTransfer();
            if (mOnCurrentTransferChangedListener != null)
                mOnCurrentTransferChangedListener.run();
        }
        return success;
    }

    public boolean cancelCurrentTransfer() {
        final boolean success = mTransferDatabase.update_transferTable_set_canceled_equalTo_where_id_equals(true, getTransferId()) > 0;
        if (success) {
            mCurrentTransfer = getLastNotFinalizedNotCanceledTransfer();
            if (mOnCurrentTransferChangedListener != null)
                mOnCurrentTransferChangedListener.run();
        }
        return success;
    }

    public void resetDatabase() {
        mTransferDatabase.getDatabase().delete(TransferDatabase.TransferTable.NAME, null, null);
        mTransferDatabase.getDatabase().delete(TransferDatabase.ItemTable.NAME, null, null);
        mCurrentTransfer = null;
        if (mOnCurrentTransferChangedListener != null)
            mOnCurrentTransferChangedListener.run();
    }

    public long getTransferId() {
        return mCurrentTransfer != null ? mCurrentTransfer.id : -1;
    }

    /*public String getPreviousPrefix() {
        return mPreviousPrefix;
    }

    public void setPreviousPrefix(@NotNull String previousPrefix) {
        this.mPreviousPrefix = previousPrefix;
    }

    public String getPreviousPostfix() {
        return mPreviousPostfix;
    }
    public void setPreviousPostfix(@NotNull String previousPostfix) {
        this.mPreviousPostfix = previousPostfix;
    }*/

    public boolean isShowingDialog() {
        return mIsShowingDialog;
    }

    public void setIsShowingDialog(boolean showingDialog) {
        this.mIsShowingDialog = showingDialog;
    }

    public boolean deleteDatabase(Context context) {
        return mTransferDatabase != null && mTransferDatabase.delete(context);
    }

    public boolean isDatabaseOpen() {
        return mTransferDatabase != null && mTransferDatabase.isOpen();
    }

    public boolean databaseExists(Context context) {
        return mTransferDatabase != null && mTransferDatabase.exists(context);
    }

    public void closeDatabase() {
        if (mTransferDatabase != null)
            mTransferDatabase.close();
    }

    public boolean isDuplicate(String itemBarcode) {
        return mTransferDatabase != null && mTransferDatabase.select_count_from_itemTable_where_transferId_equals_and_barcode_equals(getTransferId(), itemBarcode) > 0;
    }

    public int getItemCount() {
        if (getTransferId() < 0)
            return -1;
        return (int) mTransferDatabase.select_count_from_itemTable_where_transferId_equals(getTransferId());
    }

    public long insertItem(String itemBarcode) {
        return mTransferDatabase.insert_transferId_barcode_into_itemTable(getTransferId(), itemBarcode);
    }

    public long deleteItem(long itemId) {
        return mTransferDatabase.delete_from_itemTable_where_id_equals(itemId);
    }

    public Cursor getItemListCursor() {
        return mTransferDatabase.getDatabase().query(TransferDatabase.ItemTable.NAME, new String[] { TransferDatabase.Key.ID, TransferDatabase.Key.TRANSFER_ID, TransferDatabase.Key.BARCODE }, TransferDatabase.Key.TRANSFER_ID + " = ?", new String[] { String.valueOf(getTransferId()) }, null, null, TransferDatabase.Key.ID + " DESC");
    }
    /*
    public long deleteCurrentTransfer() {
        if (getTransferId() < 0)
            return -1;
        final long temp = mTransferDatabase.delete_from_transferTable_where_id_equals(getTransferId()) + mTransferDatabase.delete_from_itemTable_where_transferId_equals(getTransferId());
        mCurrentTransfer = getLastNotFinalizedNotCanceledTransfer();
        if (onCurrentTransferChangedListener != null)
            onCurrentTransferChangedListener.run();
        return temp;
    }
    */

    /**
     * Inserts a row into the {@link TransferDatabase} and sets the current transfer to a
     * {@link Transfer} object representing it
     *
     * @param locationBarcode the barcode of the location to be added to the database
     */
    public void newTransfer(@NotNull String locationBarcode) {
        mCurrentTransfer = new Transfer(mTransferDatabase.insert_locationBarcode_into_transferTable(locationBarcode), false, false, false, locationBarcode);
        if (mOnCurrentTransferChangedListener != null)
            mOnCurrentTransferChangedListener.run();
    }

    public void saveSignature(Activity activity, Bitmap bitmap) {
        asyncSaveSignature(new WeakReference<>(activity), new Utils.Toaster(activity), bitmap, new File(SIGNATURES_PATH, String.format(Locale.US, SIGNATURE_FILE_NAME, getTransferId())), new WeakReference<>(() -> mTransferDatabase.update_transferTable_set_signed_equalTo_where_id_equals(true, getTransferId())));
    }

    private void asyncSaveSignature(final WeakReference<Context> contextWeakReference, Utils.Toaster toaster, final Bitmap bitmap, File file, WeakReference<Runnable> onSuccess) {
        AsyncTask.execute(() -> {
            File tempSignature;

            try {
                tempSignature = File.createTempFile("signature", ".png", file.getParentFile());
            } catch (IOException e) {
                e.printStackTrace();
                toaster.toast("Unable to save signature");
                return;
            }

            try {
                Utils.saveSignature(contextWeakReference.get(), bitmap, tempSignature);
            } catch (IOException e) {
                e.printStackTrace();
                toaster.toast(e.getMessage());
                return;
            }

            if (!tempSignature.renameTo(file)) {
                toaster.toast("Could not rename signature file");
                return;
            }

            if (contextWeakReference.get() != null)
                Utils.refreshExternalPath(contextWeakReference.get(), file);

            toaster.toast("Saved signature");

            if (onSuccess.get() != null)
                onSuccess.get().run();
        });
    }

    public void saveTransferToFile(Activity activity, Runnable onSuccess, Utils.OnProgressUpdateListener onProgressUpdateListener, Runnable onFail) {
        asyncSaveTransferToFile(new WeakReference<>(activity), new Utils.Toaster(activity), getCurrentTransferRowQuery(), getItemsWithCurrentTransferIdQuery(), OUTPUT_FILE, new WeakReference<>(onSuccess), new WeakReference<>(onProgressUpdateListener), new WeakReference<>(onFail));
    }

    public static void asyncSaveTransferToFile(final WeakReference<Context> contextWeakReference, Utils.Toaster toaster, Utils.QueryHolder transferQuery, Utils.QueryHolder itemQuery, File file, WeakReference<Runnable> onSuccess, WeakReference<Utils.OnProgressUpdateListener> onProgressUpdateListener, WeakReference<Runnable> onFail) {
        AsyncTask.execute(() -> {
            toaster.toast("Saving...");

            final boolean fileExists = OUTPUT_FILE.exists();

            PrintStream printStream = null;

            Cursor transferCursor = transferQuery.query();
            transferCursor.moveToFirst();
            int transferLocationBarcodeIndex = transferCursor.getColumnIndex(TransferDatabase.Key.LOCATION_BARCODE);
            int transferIdIndex = transferCursor.getColumnIndex(TransferDatabase.Key.ID);
            int transferStartDateTimeIndex = transferCursor.getColumnIndex(TransferDatabase.Key.START_DATE_TIME);
            Cursor itemCursor = itemQuery.query();
            itemCursor.moveToFirst();
            int itemBarcodeIndex = itemCursor.getColumnIndex(TransferDatabase.Key.BARCODE);
            int itemDateTimeIndex = itemCursor.getColumnIndex(TransferDatabase.Key.DATE_TIME);

            try {
                printStream = new PrintStream(new FileOutputStream(OUTPUT_FILE, true));
                int updateNum = 0;
                int itemIndex = 0;
                int totalItemCount = itemCursor.getCount();

                if (!fileExists) {
                    printStream.print(BuildConfig.APPLICATION_ID.split(Pattern.quote("."))[2] + "|" + BuildConfig.BUILD_TYPE + "|v" + BuildConfig.VERSION_NAME + "|" + BuildConfig.VERSION_CODE + "\r\n");
                    printStream.flush();
                }

                printStream.printf("\"%1s\"|\"%2d\"|\"%3s\"\r\n", transferCursor.getString(transferLocationBarcodeIndex).replace("\"", "\"\""), transferCursor.getLong(transferIdIndex), transferCursor.getString(transferStartDateTimeIndex).replace("-", "/"));
                printStream.flush();

                while (!itemCursor.isAfterLast()) {
                    final float tempProgress = ((float) itemIndex) / totalItemCount;
                    if (tempProgress * 100 > updateNum) {
                        if (onProgressUpdateListener.get() != null)
                            onProgressUpdateListener.get().onProgressUpdate(tempProgress);
                        updateNum++;
                    }

                    printStream.printf("\"%1s\"|\"%2s\"\r\n", itemCursor.getString(itemBarcodeIndex).replace("\"", "\"\""), itemCursor.getString(itemDateTimeIndex).replace("-", "/"));
                    printStream.flush();

                    itemCursor.moveToNext();
                    itemIndex++;
                }
            } catch (IOException e) {
                e.printStackTrace();
                if (onFail.get() != null)
                    onFail.get().run();
                toaster.toast(e.getMessage());
                return;
            } finally {
                if (printStream != null)
                    printStream.close();
                transferCursor.close();
                itemCursor.close();
            }

            if (contextWeakReference.get() != null)
                Utils.refreshExternalPath(contextWeakReference.get(), file);

            toaster.toast("Saved");

            if (onSuccess.get() != null)
                onSuccess.get().run();
        });
    }

    public enum BarcodeType {
        Item("e1", "E", "t", "T"),
        Container("m1", "M", "A", "a"),
        Location("V", "L5"),
        Process("L3"),
        Invalid();

        private final String[] prefixes;

        BarcodeType(String... prefixes) {
            this.prefixes = prefixes;
        }

        public boolean isOfType(String barcode) {
            for (String prefix : prefixes)
                if (barcode.startsWith(prefix))
                    return true;
            return false;
        }

        public static BarcodeType getBarcodeType(String barcode) {
            if (barcode == null)
                return Invalid;
            for(BarcodeType barcodeType : BarcodeType.values())
                for (String prefix : barcodeType.prefixes)
                    if (barcode.startsWith(prefix))
                        return barcodeType;
            return Invalid;
        }
    }

    public static class Item {
        public final long id;
        public final long transferId;
        public final String barcode;

        private Item(long id, long transferId, String barcode) {
            this.id = id;
            this.transferId = transferId;
            this.barcode = barcode;
        }
    }

    public static class Transfer {
        public final long id;
        public final boolean signed;
        public final boolean finalized;
        public final boolean canceled;
        public final String locationBarcode;

        private Transfer(long id, boolean signed, boolean finalized, boolean canceled, String locationBarcode) {
            this.id = id;
            this.signed = signed;
            this.finalized = finalized;
            this.canceled = canceled;
            this.locationBarcode = locationBarcode;
        }
    }
}
