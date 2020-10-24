package com.porterlee.transfer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

import com.porterlee.transfer.Utils.Item;
import com.porterlee.transfer.Utils.Transfer;
import com.porterlee.transfer.Utils.Batch;

import static com.porterlee.transfer.Utils.constructBatchFromCursor;
import static com.porterlee.transfer.Utils.constructItemFromCursor;
import static com.porterlee.transfer.Utils.constructTransferFromCursor;

public class DataManager {
    public static final String TAG = DataManager.class.getCanonicalName();
    public static final File EXTERNAL_PATH = new File(Environment.getExternalStorageDirectory(), "Transfer");
    public static final File SIGNATURES_PATH = new File(EXTERNAL_PATH, "Signatures");
    public static final File OUTPUT_FILE = new File(EXTERNAL_PATH, "transfer.txt");
    public static final String SIGNATURE_FILE_NAME = "signature_%d.png";
    private static final String OUTPUT_FILE_HEADER = String.format(Locale.US, "%s|%s|%s|v%s|%d", BuildConfig.APPLICATION_ID.substring(BuildConfig.APPLICATION_ID.indexOf('.', BuildConfig.APPLICATION_ID.indexOf('.') + 1) + 1), BuildConfig.FLAVOR, BuildConfig.BUILD_TYPE, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
    private static final String KEY_VERSION_MAJOR = "version_major";
    private static final String KEY_VERSION_MINOR = "version_minor";
    private static final String KEY_VERSION_PATCH = "version_patch";
    private volatile TransferDatabase mTransferDatabase;
    private SharedPreferences mSharedPreferences;
    private Version mLastVersion;
    private Version mCurrentVersion = new Version(BuildConfig.VERSION_CODE);
    private Transfer mCurrentTransfer;
    private Runnable mOnCurrentBatchChangedListener;
    private Runnable mOnCurrentTransferChangedListener;
    private boolean mIsShowingDialog;
    private boolean mIsShowingModalDialog;
    private boolean mIsSaving;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private ArrayList<Object> listenerReferences = new ArrayList<>();

    public DataManager (SharedPreferences sharedPreferences) {
        mSharedPreferences = sharedPreferences;
        mLastVersion = preferences_getVersion();
    }

    public Version preferences_getVersion() {
        return new Version(
                mSharedPreferences.getInt(KEY_VERSION_MAJOR, 1),
                mSharedPreferences.getInt(KEY_VERSION_MINOR, 9),
                mSharedPreferences.getInt(KEY_VERSION_PATCH, 0)
        );
    }

    public void preferences_setVersion(Version v) {
        mSharedPreferences.edit()
                .putInt(KEY_VERSION_MAJOR, v.major)
                .putInt(KEY_VERSION_MINOR, v.minor)
                .putInt(KEY_VERSION_PATCH, v.patch)
                .apply();
    }

    public Version getLastVersion() {
        return mLastVersion;
    }

    public Version getCurrentVersion() {
        return mCurrentVersion;
    }

    public void showScannerDialog(Dialog dialog, final DialogInterface.OnDismissListener onDismissListener, ScannerUtils.OnBarcodeScannedListener onBarcodeScannedListener) {
        showDialog0(dialog, onDismissListener, onBarcodeScannedListener, false);
    }

    public void showModalScannerDialog(Dialog dialog, final DialogInterface.OnDismissListener onDismissListener) {
        showDialog0(dialog, onDismissListener, null, true);
    }

    private void showDialog0(Dialog dialog, final DialogInterface.OnDismissListener onDismissListener, final ScannerUtils.OnBarcodeScannedListener onBarcodeScannedListener, final boolean modal) {
        if (dialog == null || mIsShowingDialog) {
            if (onDismissListener != null)
                onDismissListener.onDismiss(null);
            return;
        }
        final ScannerUtils.OnBarcodeScannedListener temp = getScannerUtils().getOnBarcodeScannedListener();
        setIsShowingDialog(true);
        setIsShowingModalDialog(modal);
        if (!modal) getScannerUtils().setOnBarcodeScannedListener(onBarcodeScannedListener);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (!modal) getScannerUtils().setOnBarcodeScannedListener(temp);
                setIsShowingDialog(false);
                setIsShowingModalDialog(false);
                if (onDismissListener != null)
                    onDismissListener.onDismiss(dialog);
            }
        });
        dialog.show();
    }

    public boolean isSaving() {
        return mIsSaving;
    }

    public void init(Context context) {
        mTransferDatabase = new TransferDatabase(context);
        setCurrentTransfer(query_getLastActiveTransfer());

        if (!EXTERNAL_PATH.mkdirs() && !EXTERNAL_PATH.exists())
            Log.w(TAG, "External directory does not exist and could not be created, this may cause a problem");

        if (!SIGNATURES_PATH.mkdirs() && !SIGNATURES_PATH.exists())
            Log.w(TAG, "Signature directory does not exist and could not be created, this may cause a problem");
    }

    public static Scanner getScanner() {
        return Scanner.getInstance();
    }

    public static ScannerUtils getScannerUtils() {
        return ScannerUtils.getInstance();
    }

    public Runnable getOnCurrentBatchChangedListener() {
        return mOnCurrentBatchChangedListener;
    }

    public void setOnCurrentBatchChangedListener(Runnable onCurrentBatchChangedListener) {
        this.mOnCurrentBatchChangedListener = onCurrentBatchChangedListener;
    }

    public Runnable getOnCurrentTransferChangedListener() {
        return mOnCurrentTransferChangedListener;
    }

    public void setOnCurrentTransferChangedListener(Runnable onCurrentTransferChangedListener) {
        this.mOnCurrentTransferChangedListener = onCurrentTransferChangedListener;
    }

    private Item query_getItem(long id) {
        final Cursor cursor = mTransferDatabase.query_getItem(id);
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        Item temp = constructItemFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Transfer query_getTransfer(long id) {
        final Cursor cursor = mTransferDatabase.query_getTransfer(id);
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        Transfer temp = constructTransferFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Transfer query_getLastTransfer() {
        final Cursor cursor = mTransferDatabase.query_getLastTransfer();
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        Transfer temp = constructTransferFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Transfer query_getLastActiveTransfer() {
        final Cursor cursor = mTransferDatabase.query_getLastActiveTransfer();
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        Transfer temp = constructTransferFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Transfer query_getLastTransferWithBatchId(long batchId) {
        if (batchId < 0)
            return null;
        final Cursor cursor = mTransferDatabase.query_getLastTransferWithBatchId(batchId);
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        Transfer temp = constructTransferFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Transfer query_getLastActiveTransferWithBatchId(long batchId) {
        if (batchId < 0)
            return null;
        final Cursor cursor = mTransferDatabase.query_getLastActiveTransferWithBatchId(batchId);
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        Transfer temp = constructTransferFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Batch query_getBatch(long id) {
        final Cursor cursor = mTransferDatabase.query_getBatch(id);
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        Batch temp = constructBatchFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Batch query_getLastBatch() {
        final Cursor cursor = mTransferDatabase.query_getLastBatch();
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        Batch temp = constructBatchFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private long query_getLastBatchID() {
        if (mTransferDatabase.query_getBatchCount() <= 0)  {
            return mTransferDatabase.query_getLastBatchId();
        } else {
            return  -1;
        }
    }

    public Transfer getCurrentTransfer() {
        return mCurrentTransfer;
    }
/*
    public boolean query_updateCurrentTransferSetBatchId(long batchId) {
        final boolean success = mTransferDatabase.query_updateTransferSetBatchId(getCurrentTransferId(), batchId) > 0;
        if (success) {
            setCurrentTransfer(query_getLastActiveTransfer());
        }
        return success;
    }
*/
    public boolean query_updateCurrentTransferSetCanceled() {
        final boolean success = mTransferDatabase.query_updateTransferSetCanceled(getCurrentTransferId()) > 0;
        if (success) {
            setCurrentTransfer(query_getLastActiveTransfer());
        }
        return success;
    }

    public boolean query_updateCurrentTransferSetFinalized() {
        final boolean success = mTransferDatabase.query_updateTransferSetFinalized(getCurrentTransferId()) > 0;
        if (success) {
            setCurrentTransfer(query_getLastActiveTransfer());
        }
        return success;
    }
/*
    public boolean query_updateTransfersSetCanceledIfNotFinalized() {
        final boolean success = mTransferDatabase.query_updateTransfersSetCanceledIfNotFinalized() > 0;
        if (success)
            setCurrentTransfer(query_getLastTransfer());
        return success;
    }
*/
    private void setCurrentTransfer(Transfer transfer) {
        mCurrentTransfer = transfer;
        if (mOnCurrentTransferChangedListener != null)
            mOnCurrentTransferChangedListener.run();
    }

    public void switchToPreviousTransfer() {
        long transferId = getCurrentTransferId();
        if (transferId >= 0) {
            if (mTransferDatabase.query_getPreviousTransferCount(transferId) > 0) {
                setCurrentTransfer(constructTransferFromCursor(mTransferDatabase.query_getPreviousTransfer(transferId)));
            } else {
                // no previous transfer
            }
        } else {
            setCurrentTransfer(query_getLastTransfer());
        }
    }

    public void switchToNextTransfer() {
        long transferId = getCurrentTransferId();
        if (transferId >= 0) {
            if (mTransferDatabase.query_getNextTransferCount(transferId) > 0) {
                setCurrentTransfer(constructTransferFromCursor(mTransferDatabase.query_getNextTransfer(transferId)));
            } else {
                setCurrentTransfer(null); // possible to start a new transfer now
            }
        }
    }

    public boolean query_hasPreviousTransfer() {
        long transferId = getCurrentTransferId();
        if (transferId >= 0) {
            if (mTransferDatabase.query_getPreviousTransferCount(transferId) > 0) {
                return true;
            } else {
                return false; // no previous transfer
            }
        }
        return true;
    }

    public boolean query_hasNextTransfer() {
        long transferId = getCurrentTransferId();
        if (transferId >= 0) {
            if (mTransferDatabase.query_getNextTransferCount(transferId) > 0) {
                return true;
            } else {
                return true; // next transfer is null
            }
        }
        return false;
    }

    public boolean query_hasActiveTransfer() {
        return mTransferDatabase.query_getActiveTransferCount() > 0;
    }

    public void switchToNullTransfer() {
        setCurrentTransfer(null);
    }
/*
    public void reset(Context context) {
        //mTransferDatabase.getDatabase().delete(TransferDatabase.TransferTable.NAME, null, null);
        //mTransferDatabase.getDatabase().delete(TransferDatabase.ItemTable.NAME, null, null);
        query_updateTransfersSetCanceledIfNotFinalized();
        Iterator<File> fileIterator =  FileUtils.iterateFiles(EXTERNAL_PATH, new IOFileFilter() {
            @Override
            public boolean accept(File file) {
                return true;
            }

            @Override
            public boolean accept(File dir, String name) {
                return false;
            }
        }, new IOFileFilter() {
            @Override
            public boolean accept(File file) {
                return true;
            }

            @Override
            public boolean accept(File dir, String name) {
                return false;
            }
        });

        while (fileIterator.hasNext()) {
            try {
                final File file = fileIterator.next();
                FileUtils.forceDelete(file);
                Utils.refreshExternalFile(context, file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
*/
    public long getCurrentTransferId() {
        return mCurrentTransfer != null ? mCurrentTransfer.id : -1;
    }

    public long getCurrentBatchId() {
        return mCurrentTransfer != null ? mCurrentTransfer.batch_id : -1;
    }

    public boolean isShowingDialog() {
        return mIsShowingDialog;
    }

    private void setIsShowingDialog(boolean showingDialog) {
        this.mIsShowingDialog = showingDialog;
        updateScannerIsDisabled();
    }

    public boolean isShowingModalDialog() {
        return mIsShowingDialog;
    }

    private void setIsShowingModalDialog(boolean showingModalDialog) {
        this.mIsShowingModalDialog = showingModalDialog;
        updateScannerIsDisabled();
    }

    private void setIsSaving(boolean isSaving) {
        this.mIsSaving = isSaving;
        updateScannerIsDisabled();
    }

    private void updateScannerIsDisabled() {
        getScanner().setIsEnabled(!mIsShowingModalDialog && !mIsSaving);
    }

    public boolean deleteDatabase(Context context) {
        return mTransferDatabase != null && mTransferDatabase.delete(context);
    }

    public boolean databaseExists(Context context) {
        return mTransferDatabase != null && mTransferDatabase.exists(context);
    }

    public void closeDatabase() {
        mTransferDatabase.close();
    }

    public Cursor query_getItems() {
        long transferId = getCurrentTransferId();
        return transferId < 0 ? null : mTransferDatabase.query_getItemsWithTransferId(transferId);
    }

    public Cursor query_getLastItemWithBarcode(String barcode) {
        long transferId = getCurrentTransferId();
        return transferId < 0 ? null : mTransferDatabase.query_getLastItemWithBarcode(barcode);
    }

    public boolean query_currentTransferHasItemWithBarcode(String itemBarcode) {
        long transferId = getCurrentTransferId();
        return mTransferDatabase != null && transferId >= 0 && (mTransferDatabase.query_getItemCountWithTransferIdAndBarcode(transferId, itemBarcode) > 0);
    }

    public int query_getItemCount() {
        long transferId = getCurrentTransferId();
        return transferId < 0 ? -1 : (int) mTransferDatabase.query_getItemCountWithTransferId(transferId);
    }

    public boolean query_updateItemQuantity(long itemId, int quantity) {
        return mTransferDatabase.query_updateItemQuantity(itemId, quantity) > 0;
    }

    public long query_insertItem(String itemBarcode) {
        long transferId = getCurrentTransferId();
        return transferId < 0 ? -1 : mTransferDatabase.query_insertItem(transferId, itemBarcode, 1);
    }

    public long query_deleteItem(long itemId) {
        return mTransferDatabase.query_deleteItem(itemId);
    }
    /*
    public long deleteCurrentTransfer() {
        if (getTransferId() < 0)
            return -1;
        final long temp = mTransferDatabase.delete_from_transferTable_where_id_equals(getTransferId()) + mTransferDatabase.delete_from_itemTable_where_transferId_equals(getTransferId());
        mCurrentTransfer = getLastUnfinishedTransfer();
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
    public long startNewTransfer(@NonNull String locationBarcode) {
        query_insertBatchIfNotExist();

        setCurrentTransfer(query_getTransfer(mTransferDatabase.query_insertTransfer(mTransferDatabase.query_getLastBatchId(), locationBarcode)));
        return getCurrentTransferId();
    }

    public void signCurrentTransfer(final Context context, Bitmap bitmap, final Utils.DetailedOnFinishListener onFinishListener) {
        final Transfer transfer = getCurrentTransfer();
        final Utils.DetailedOnFinishListener temp = new Utils.DetailedOnFinishListener() {
            @Override
            public void onFinish(boolean success, String message) {
                if (success) {
                    success = mTransferDatabase.query_updateTransferSetSigned(transfer.id) > 0;
                    if (success)
                        setCurrentTransfer(query_getTransfer(transfer.id));
                    else
                        message = "Error signing: " + message;
                }

                if (onFinishListener != null)
                    onFinishListener.onFinish(success, message);
                listenerReferences.remove(this);
            }
        };
        listenerReferences.add(temp);

        if (transfer != null) {
            asyncSaveSignature(context, bitmap, transfer.id, temp);
        } else {
            onFinishListener.onFinish(false, "no current transfer");
        }
    }

    private static void asyncSaveSignature(Context context, final Bitmap bitmap, long transferId, Utils.DetailedOnFinishListener onFinishListener) {
        final WeakReference<Context> contextWeakReference = new WeakReference<>(context.getApplicationContext());
        context = null;
        final File file = new File(SIGNATURES_PATH, String.format(Locale.US, SIGNATURE_FILE_NAME, transferId));
        final WeakReference<Utils.DetailedOnFinishListener> weakOnFinishListener = new WeakReference<>(onFinishListener);
        onFinishListener = null;
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                File tempSignature;

                try {
                    tempSignature = File.createTempFile("signature", ".png", file.getParentFile());
                } catch (IOException e) {
                    e.printStackTrace();
                    if (weakOnFinishListener.get() != null)
                        weakOnFinishListener.get().onFinish(false, "Unable to save signature");
                    return;
                }

                try {
                    Utils.saveSignature(contextWeakReference.get(), bitmap, tempSignature);
                } catch (IOException e) {
                    e.printStackTrace();
                    if (weakOnFinishListener.get() != null)
                        weakOnFinishListener.get().onFinish(false, e.getMessage());
                    return;
                }

                if (!tempSignature.renameTo(file)) {
                    if (weakOnFinishListener.get() != null)
                        weakOnFinishListener.get().onFinish(false, "Could not rename signature file");
                    return;
                }

                if (contextWeakReference.get() != null)
                    Utils.refreshExternalFile(contextWeakReference.get(), file);

                if (weakOnFinishListener.get() != null)
                    weakOnFinishListener.get().onFinish(true, "Saved signature");
            }
        });
    }

    public void finalizeCurrentTransfer(Utils.DetailedOnFinishListener onFinishListener) {
        if (mTransferDatabase.query_updateTransferSetFinalized(getCurrentTransferId()) > 0) {
            onFinishListener.onFinish(true, "Finalized");
        } else {
            onFinishListener.onFinish(true, "Error updating database");
        }
    }

    public void query_insertBatchIfNotExist() {
        if (mTransferDatabase.query_getBatchCount() > 0)
            return;

        query_insertBatch();
    }

    public void query_insertBatch() {
        if (mTransferDatabase.query_insertBatch() < 0)
            throw new RuntimeException("could not create new batch");
    }

    public void updateOldInProgressTransfersBatchIds() {
        query_insertBatchIfNotExist();

        if (OUTPUT_FILE.exists()) {
            mTransferDatabase.query_updateTransfersSetBatchId(getSavedTransfers_old(OUTPUT_FILE), mTransferDatabase.query_getLastBatchId());
        }
    }

    @NonNull
    public ArrayList<Transfer> getSavedTransfers_old(File file) {
        ArrayList<Transfer> transfers = new ArrayList<>();

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String header = reader.readLine();
            if (header == null)
                return new ArrayList<>();

            String line;
            long lastId = -1;
            while ((line = reader.readLine()) != null) {
                String[] entries = line.split("\\|");
                final String barcode = entries[0].substring(1, entries[0].length() - 1);
                final long id = Long.parseLong(entries[1].substring(1, entries[1].length() - 1));
                final String start_datetime = entries[2].substring(1, entries[2].length() - 1).replace('/', '-');
                if (lastId != id) {
                    lastId = id;
                    transfers.add(new Transfer(
                            id,
                            -1,
                            barcode,
                            false,
                            false,
                            false,
                            false,
                            start_datetime,
                            null,
                            null,
                            null,
                            null,
                            null

                    ));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return transfers;
    }

    public void saveCurrentBatch(final Activity activity, final Utils.OnProgressUpdateListener onProgressUpdateListener, final Utils.DetailedOnFinishListener onFinishListener) {
        setIsSaving(true);
        final Utils.DetailedOnFinishListener temp = new Utils.DetailedOnFinishListener() {
            @Override
            public void onFinish(boolean success, String message) {
                setIsSaving(false);
                if (success) {
                    success = query_updateCurrentTransferSetFinalized();
                    if (success) {
                        setCurrentTransfer(query_getLastActiveTransfer());
                    } else {
                        message = "Error finalizing: " + message;
                    }
                }

                onFinishListener.onFinish(success, message);
                listenerReferences.remove(this);
                listenerReferences.remove(onFinishListener);
                listenerReferences.remove(onProgressUpdateListener);
            }
        };
        listenerReferences.add(temp);
        listenerReferences.add(onFinishListener);
        listenerReferences.add(onProgressUpdateListener);
        asyncSaveBatchToFile(activity, mTransferDatabase, getCurrentBatchId(), onProgressUpdateListener, temp);
    }

    public static void asyncSaveBatchToFile(Activity activity, TransferDatabase transferDatabase, final long batchId, Utils.OnProgressUpdateListener onProgressUpdateListener, Utils.DetailedOnFinishListener onFinishListener) {
        final WeakReference<Context> context_weak = new WeakReference<>(activity.getApplicationContext());
        activity = null;
        final WeakReference<TransferDatabase> transferDatabase_weak = new WeakReference<>(transferDatabase);
        transferDatabase = null;
        final WeakReference<Utils.OnProgressUpdateListener> onProgressUpdateListener_weak = new WeakReference<>(onProgressUpdateListener);
        onProgressUpdateListener = null;
        final WeakReference<Utils.DetailedOnFinishListener> onFinishListener_weak = new WeakReference<>(onFinishListener);
        onFinishListener = null;
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                PrintStream printStream = null;
                Cursor transferCursor = null;
                Cursor itemCursor = null;

                if (transferDatabase_weak.get() != null) {
                    transferCursor = transferDatabase_weak.get().query_getFinalTransfersWithBatchId(batchId);
                } else {
                    return;
                }
                transferCursor.moveToFirst();
                int transferIdIndex = transferCursor.getColumnIndex(TransferDatabase.Key.ID);
                int transferLocationBarcodeIndex = transferCursor.getColumnIndex(TransferDatabase.Key.LOCATION_BARCODE);
                int transferStartDateTimeIndex = transferCursor.getColumnIndex(TransferDatabase.Key.START_DATETIME);

                try {
                    OUTPUT_FILE.setWritable(true, true);
                    printStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(OUTPUT_FILE)));
                    printStream.print(OUTPUT_FILE_HEADER + "\r\n");
                    printStream.flush();

                    while (!transferCursor.isAfterLast()) {

                        if (transferDatabase_weak.get() != null) {
                            itemCursor = transferDatabase_weak.get().query_getItemsWithTransferId(transferCursor.getLong(transferIdIndex));
                        } else {
                            return;
                        }
                        itemCursor.moveToFirst();
                        int itemBarcodeIndex = itemCursor.getColumnIndex(TransferDatabase.Key.BARCODE);
                        int itemQuantityIndex = itemCursor.getColumnIndex(TransferDatabase.Key.QUANTITY);
                        int itemDateTimeIndex = itemCursor.getColumnIndex(TransferDatabase.Key.SCAN_DATETIME);

                        int updateNum = 0;
                        int itemIndex = 0;
                        int totalItemCount = itemCursor.getCount();

                        printStream.printf("\"%s\"|\"%d\"|\"%s\"\r\n", transferCursor.getString(transferLocationBarcodeIndex).replace("\"", "\"\""), transferCursor.getLong(transferIdIndex), transferCursor.getString(transferStartDateTimeIndex).replace("-", "/"));
                        printStream.flush();

                        while (!itemCursor.isAfterLast()) {
                            final float tempProgress = ((float) itemIndex) / totalItemCount;
                            if (tempProgress * 100 > updateNum) {
                                if (onProgressUpdateListener_weak.get() != null)
                                    onProgressUpdateListener_weak.get().onProgressUpdate(tempProgress);
                                updateNum++;
                            }

                            if (BuildConfig.ui_enableQuantityEdit) {
                                printStream.printf("\"%s\"|\"%d\"|\"%d\"|\"%s\"\r\n", itemCursor.getString(itemBarcodeIndex).replace("\"", "\"\""), itemCursor.getLong(itemQuantityIndex), transferCursor.getLong(transferIdIndex), itemCursor.getString(itemDateTimeIndex).replace("-", "/"));
                            } else {
                                printStream.printf("\"%s\"|\"%d\"|\"%s\"\r\n", itemCursor.getString(itemBarcodeIndex).replace("\"", "\"\""), transferCursor.getLong(transferIdIndex), itemCursor.getString(itemDateTimeIndex).replace("-", "/"));
                            }

                            itemCursor.moveToNext();
                            itemIndex++;
                        }

                        if (transferDatabase_weak.get() != null) {
                            itemCursor = transferDatabase_weak.get().query_getItemsWithTransferId(transferCursor.getLong(transferIdIndex));
                        } else {
                            return;
                        }
                        transferCursor.moveToNext();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (onFinishListener_weak.get() != null)
                        onFinishListener_weak.get().onFinish(false, e.getMessage());
                    return;
                } finally {
                    if (printStream != null)
                        printStream.close();
                    if (transferCursor != null)
                        transferCursor.close();
                    if (itemCursor != null)
                        itemCursor.close();
                }

                if (context_weak.get() != null)
                    Utils.refreshExternalFile(context_weak.get(), OUTPUT_FILE);

                if (onFinishListener_weak.get() != null)
                    onFinishListener_weak.get().onFinish(true, "Saved");
            }
        });
    }
}
