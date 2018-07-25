package com.porterlee.limstransfer;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.porterlee.plcscanners.AbstractScanner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;

import org.jetbrains.annotations.NotNull;

public class DataManager {
    public static final String TAG = DataManager.class.getCanonicalName();
    public static final File EXTERNAL_PATH = new File(Environment.getExternalStorageDirectory(), "Transfer");
    public static final File SIGNATURES_PATH = new File(EXTERNAL_PATH, "Signatures");
    public static final File OUTPUT_FILE = new File(EXTERNAL_PATH, "transfer.txt");
    public static final String SIGNATURE_FILE_NAME = "signature_%d.png";
    private static final String OUTPUT_FILE_HEADER = String.format(Locale.US, "%s|%s|%s|v%s|%d", BuildConfig.APPLICATION_ID.split("\\.")[2], BuildConfig.FLAVOR, BuildConfig.BUILD_TYPE, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
    private static final String KEY_REQUIRES_STARTUP_LOGIN = "requires_startup_login";
    private static final String KEY_REQUIRES_ANALYST_LOGIN = "requires_analyst_login";
    private static final String KEY_REQUIRES_SIGNATURE = "requires_signature";
    private volatile TransferDatabase mTransferDatabase;
    private SharedPreferences mSharedPreferences;
    private Transfer mCurrentTransfer;
    private Runnable mOnCurrentTransferChangedListener;
    private boolean mIsShowingDialog;
    private boolean mIsShowingModalDialog;
    private boolean mIsSaving;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private ArrayList<Object> listenerReferences = new ArrayList<>();
    private ArrayList<AbstractScanner.OnBarcodeScannedListener> onBarcodeScannedListenersQueue = new ArrayList<>();

    public DataManager (Activity activity) {
        mSharedPreferences = activity.getPreferences(Context.MODE_PRIVATE);
        AbstractScanner.setActivity(activity);
    }

    public void showScannerDialog(Dialog dialog, final DialogInterface.OnDismissListener onDismissListener, AbstractScanner.OnBarcodeScannedListener onBarcodeScannedListener) {
        showDialog0(dialog, onDismissListener, onBarcodeScannedListener, false);
    }

    public void showDialog(Dialog dialog, final DialogInterface.OnDismissListener onDismissListener) {
        showDialog0(dialog, onDismissListener, null, true);
    }

    private void showDialog0(Dialog dialog, final DialogInterface.OnDismissListener onDismissListener, AbstractScanner.OnBarcodeScannedListener onBarcodeScannedListener, final boolean disableScanner) {
        if (dialog == null || mIsShowingDialog) {
            onDismissListener.onDismiss(null);
            return;
        }
        setIsShowingDialog(true);
        setIsShowingModalDialog(disableScanner);
        pushOnBarcodeScannedListener(onBarcodeScannedListener);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                popOnBarcodeScannedListener();
                setIsShowingDialog(false);
                setIsShowingModalDialog(false);
                if (onDismissListener != null)
                    onDismissListener.onDismiss(dialog);
            }
        });
        dialog.show();
    }

    public boolean requiresStartupLogin() {
        //todo change to false
        return mSharedPreferences.getBoolean(KEY_REQUIRES_STARTUP_LOGIN, true);
    }

    public boolean requiresAnalystLogin() {
        //todo change to false
        return mSharedPreferences.getBoolean(KEY_REQUIRES_ANALYST_LOGIN, true);
    }

    public boolean requiresSignature() {
        //todo change to false
        return mSharedPreferences.getBoolean(KEY_REQUIRES_SIGNATURE, true);
    }

    public boolean isSaving() {
        return mIsSaving;
    }

    public boolean initScanner() {
        return getScanner().init();
    }

    public void init(Context context) {
        mTransferDatabase = new TransferDatabase(context);
        updateCurrentTransfer(getLastUnfinishedTransfer());

        if (!EXTERNAL_PATH.mkdirs() && !EXTERNAL_PATH.exists())
            Log.w(TAG, "External directory does not exist and could not be created, this may cause a problem");

        if (!SIGNATURES_PATH.mkdirs() && !SIGNATURES_PATH.exists())
            Log.w(TAG, "Signature directory does not exist and could not be created, this may cause a problem");
    }

    public AbstractScanner getScanner() {
        return AbstractScanner.getInstance();
    }

    public Runnable getOnCurrentTransferChangedListener() {
        return mOnCurrentTransferChangedListener;
    }

    public void setOnCurrentTransferChangedListener(Runnable onCurrentTransferChangedListener) {
        this.mOnCurrentTransferChangedListener = onCurrentTransferChangedListener;
    }

    private Utils.QueryHolder getCurrentTransferRowQuery() {
        return new Utils.QueryHolder(mTransferDatabase.getDatabase(), "SELECT " + TransferDatabase.Key.ID + ", " + TransferDatabase.Key.LOCATION_BARCODE + ", " + TransferDatabase.Key.START_DATE_TIME + " FROM " + TransferDatabase.TransferTable.NAME + " WHERE " + TransferDatabase.Key.ID + " = ?", String.valueOf(getCurrentTransferId()));
    }

    private Utils.QueryHolder getCurrentTransferRowItemQuery() {
        return new Utils.QueryHolder(mTransferDatabase.getDatabase(), "SELECT " + TransferDatabase.Key.BARCODE + ", " + TransferDatabase.Key.DATE_TIME + " FROM " + TransferDatabase.ItemTable.NAME + " WHERE " + TransferDatabase.Key.TRANSFER_ID + " = ? ORDER BY " + TransferDatabase.Key.ID, String.valueOf(getCurrentTransferId()));
    }

    private Transfer getLastUnfinishedTransfer() {
        final Cursor cursor = mTransferDatabase.getDatabase().query(TransferDatabase.TransferTable.NAME, new String[] { TransferDatabase.Key.ID, TransferDatabase.Key.SIGNED, TransferDatabase.Key.FINALIZED, TransferDatabase.Key.CANCELED, TransferDatabase.Key.LOCATION_BARCODE }, TransferDatabase.Key.FINALIZED + " = ? AND " + TransferDatabase.Key.CANCELED + " = ?", new String[] { String.valueOf(0), String.valueOf(0) }, null, null, TransferDatabase.Key.ID + " DESC", "1");
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
        boolean success = mTransferDatabase.update_transferTable_set_finalized_equalTo_where_id_equals(true, getCurrentTransferId()) > 0;
        if (success)
            updateCurrentTransfer(getLastUnfinishedTransfer());
        return success;
    }

    public boolean cancelCurrentTransfer() {
        final boolean success = mTransferDatabase.update_transferTable_set_canceled_equalTo_where_id_equals(true, getCurrentTransferId()) > 0;
        if (success)
            updateCurrentTransfer(getLastUnfinishedTransfer());
        return success;
    }

    public boolean cancelAllNonFinalTransfers() {
        final boolean success = mTransferDatabase.update_transferTable_set_canceled_equalTo_where_finalized_equals(true, false) > 0;
        if (success)
            updateCurrentTransfer(null);
        return success;
    }

    private void updateCurrentTransfer(Transfer transfer) {
        mCurrentTransfer = transfer;
        if (mOnCurrentTransferChangedListener != null)
            mOnCurrentTransferChangedListener.run();
    }

    public boolean hasOngoingTransfer() {
        return mCurrentTransfer != null && !mCurrentTransfer.finalized;
    }

    public void reset(Context context) {
        //mTransferDatabase.getDatabase().delete(TransferDatabase.TransferTable.NAME, null, null);
        //mTransferDatabase.getDatabase().delete(TransferDatabase.ItemTable.NAME, null, null);
        cancelAllNonFinalTransfers();
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

    public long getCurrentTransferId() {
        return mCurrentTransfer != null ? mCurrentTransfer.id : -1;
    }

    public boolean isShowingDialog() {
        return mIsShowingDialog;
    }

    private void setIsShowingDialog(boolean showingDialog) {
        Log.e(TAG, "setIsShowingDialog");
        this.mIsShowingDialog = showingDialog;
        updateScannerIsDisabled();
    }

    private void setIsShowingModalDialog(boolean showingModalDialog) {
        Log.e(TAG, "setIsShowingModalDialog");
        this.mIsShowingModalDialog = showingModalDialog;
        updateScannerIsDisabled();
    }

    private void setIsSaving(boolean isSaving) {
        Log.e(TAG, "setIsSaving");
        this.mIsSaving = isSaving;
        updateScannerIsDisabled();
    }

    private void updateScannerIsDisabled() {
        getScanner().setIsEnabled(!mIsShowingModalDialog && !mIsSaving);
    }

    public void pushOnBarcodeScannedListener(AbstractScanner.OnBarcodeScannedListener onBarcodeScannedListener) {
        onBarcodeScannedListenersQueue.add(AbstractScanner.getOnBarcodeScannedListener());
        AbstractScanner.setOnBarcodeScannedListener(onBarcodeScannedListener);
    }

    public AbstractScanner.OnBarcodeScannedListener popOnBarcodeScannedListener() {
        AbstractScanner.OnBarcodeScannedListener temp = AbstractScanner.getOnBarcodeScannedListener();
        int index = onBarcodeScannedListenersQueue.size() - 1;
        AbstractScanner.setOnBarcodeScannedListener(index < 0 ? null : onBarcodeScannedListenersQueue.remove(index));
        return temp;
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
        return mTransferDatabase != null && mTransferDatabase.select_count_from_itemTable_where_transferId_equals_and_barcode_equals(getCurrentTransferId(), itemBarcode) > 0;
    }

    public int getItemCount() {
        long id = getCurrentTransferId();
        return id < 0 ? -1 : (int) mTransferDatabase.select_count_from_itemTable_where_transferId_equals(id);
    }

    public long insertItem(String itemBarcode) {
        return mTransferDatabase.insert_transferId_barcode_into_itemTable(getCurrentTransferId(), itemBarcode);
    }

    public long deleteItem(long itemId) {
        return mTransferDatabase.delete_from_itemTable_where_id_equals(itemId);
    }

    public Cursor getItemListCursor() {
        return mTransferDatabase.getDatabase().query(TransferDatabase.ItemTable.NAME, new String[] { TransferDatabase.Key.ID, TransferDatabase.Key.TRANSFER_ID, TransferDatabase.Key.BARCODE }, TransferDatabase.Key.TRANSFER_ID + " = ?", new String[] { String.valueOf(getCurrentTransferId()) }, null, null, TransferDatabase.Key.ID + " DESC");
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
    public long newTransfer(@NotNull String locationBarcode) {
        updateCurrentTransfer(new Transfer(mTransferDatabase.insert_locationBarcode_into_transferTable(locationBarcode), false, false, false, locationBarcode));
        return mCurrentTransfer.id;
    }

    public void saveSignature(final Activity activity, Bitmap bitmap, final Transfer transfer, final Utils.DetailedOnFinishListener onFinishListener) {
        final Utils.DetailedOnFinishListener temp = new Utils.DetailedOnFinishListener() {
            @Override
            public void onFinish(boolean success, final String message) {
                if (success) {
                    mTransferDatabase.update_transferTable_set_signed_equalTo_where_id_equals(true, transfer.id);
                    transfer.signed = true;
                }

                if (onFinishListener != null)
                    onFinishListener.onFinish(success, message);
                listenerReferences.remove(this);
            }
        };
        listenerReferences.add(temp);

        asyncSaveSignature(activity, bitmap, transfer.id, temp);
    }

    private static void asyncSaveSignature(Activity activity, final Bitmap bitmap, long transferId, Utils.DetailedOnFinishListener onFinishListener) {
        final WeakReference<Context> contextWeakReference = new WeakReference<>(activity.getApplicationContext());
        activity = null;
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

    public void saveTransferToFile(final Activity activity, final Utils.OnProgressUpdateListener onProgressUpdateListener, final Utils.OnFinishListener onFinishListener) {
        setIsSaving(true);
        pushOnBarcodeScannedListener(null);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity.getApplicationContext(), "Saving...", Toast.LENGTH_SHORT).show();
            }
        });
        final Utils.DetailedOnFinishListener temp = new Utils.DetailedOnFinishListener() {
            @Override
            public void onFinish(final boolean success, final String message) {
                popOnBarcodeScannedListener();
                setIsSaving(false);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity.getApplicationContext(), message, success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                    }
                });
                onFinishListener.onFinish(success);
                listenerReferences.remove(this);
                listenerReferences.remove(onProgressUpdateListener);
            }
        };
        listenerReferences.add(temp);
        listenerReferences.add(onProgressUpdateListener);
        asyncSaveTransferToFile(activity, getCurrentTransferRowQuery(), getCurrentTransferRowItemQuery(), onProgressUpdateListener, temp);
    }

    public static void asyncSaveTransferToFile(Activity activity, final Utils.QueryHolder transferQuery, final Utils.QueryHolder itemQuery, Utils.OnProgressUpdateListener onProgressUpdateListener, Utils.DetailedOnFinishListener onFinishListener) {
        final WeakReference<Context> contextWeakReference = new WeakReference<>(activity.getApplicationContext());
        activity = null;
        final WeakReference<Utils.OnProgressUpdateListener> weakOnProgressUpdateListener = new WeakReference<>(onProgressUpdateListener);
        onProgressUpdateListener = null;
        final WeakReference<Utils.DetailedOnFinishListener> weakOnFinishListener = new WeakReference<>(onFinishListener);
        onFinishListener = null;
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
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
                        printStream.print(OUTPUT_FILE_HEADER + "\r\n");
                        printStream.flush();
                    }

                    printStream.printf("\"%s\"|\"%d\"|\"%s\"\r\n", transferCursor.getString(transferLocationBarcodeIndex).replace("\"", "\"\""), transferCursor.getLong(transferIdIndex), transferCursor.getString(transferStartDateTimeIndex).replace("-", "/"));
                    printStream.flush();

                    while (!itemCursor.isAfterLast()) {
                        final float tempProgress = ((float) itemIndex) / totalItemCount;
                        if (tempProgress * 100 > updateNum) {
                            if (weakOnProgressUpdateListener.get() != null)
                                weakOnProgressUpdateListener.get().onProgressUpdate(tempProgress);
                            updateNum++;
                        }

                        printStream.printf("\"%s\"|\"%d\"|\"%s\"\r\n", itemCursor.getString(itemBarcodeIndex).replace("\"", "\"\""), transferCursor.getLong(transferIdIndex), itemCursor.getString(itemDateTimeIndex).replace("-", "/"));
                        printStream.flush();

                        itemCursor.moveToNext();
                        itemIndex++;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (weakOnFinishListener.get() != null)
                        weakOnFinishListener.get().onFinish(false, e.getMessage());
                    return;
                } finally {
                    if (printStream != null)
                        printStream.close();
                    transferCursor.close();
                    itemCursor.close();
                }

                if (contextWeakReference.get() != null)
                    Utils.refreshExternalFile(contextWeakReference.get(), OUTPUT_FILE);

                if (weakOnFinishListener.get() != null)
                    weakOnFinishListener.get().onFinish(true, "Saved");
            }
        });
    }

    public Analyst getAnalyst(String analystId) {
        final Cursor cursor = mTransferDatabase.getDatabase().query(TransferDatabase.AnalystTable.NAME, new String[] { TransferDatabase.Key.ID, TransferDatabase.Key.ANALYST_ID, TransferDatabase.Key.ANALYST_PASSWORD_SHA_1, TransferDatabase.Key.ANALYST_DESCRIPTION }, TransferDatabase.Key.ANALYST_ID + " = ?", new String[] { analystId }, null, null, null, "1");
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        Analyst temp = new Analyst(cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.ID)), cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.ANALYST_ID)), cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.ANALYST_PASSWORD_SHA_1)), cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.ANALYST_DESCRIPTION)));
        cursor.close();
        return temp;
    }

    public static class Item {
        private final long id;
        private final long transferId;
        private final String barcode;

        private Item(long id, long transferId, String barcode) {
            this.id = id;
            this.transferId = transferId;
            this.barcode = barcode;
        }
    }

    public static class Transfer {
        private final long id;
        private boolean signed;
        private boolean finalized;
        private boolean canceled;
        private final String locationBarcode;

        private Transfer(long id, boolean signed, boolean finalized, boolean canceled, String locationBarcode) {
            this.id = id;
            this.signed = signed;
            this.finalized = finalized;
            this.canceled = canceled;
            this.locationBarcode = locationBarcode;
        }

        public boolean isSigned() {
            return signed;
        }

        public boolean isFinalized() {
            return finalized;
        }

        public boolean isCanceled() {
            return canceled;
        }

        public String getLocationBarcode() {
            return locationBarcode;
        }
    }

    public static class Analyst {
        public final long id;
        public final String analystId;
        public final String password;
        public final String analystDescription;

        private Analyst(long id, @NonNull String analystId, @NonNull String password, @NonNull String analystDescription) {
            this.id = id;
            this.analystId = analystId;
            this.password = password;
            this.analystDescription = analystDescription;
        }

        public boolean verifyAnalystLogin(String password) {
            try {
                return password.equals(Utils.SHA1(analystId + password))  ;
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "\"SHA-1\" algorithm not found");
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "\"US-ASCII\" charset not found");
                e.printStackTrace();
            }

            return false;
        }

        public String getAnalystId() {
            return analystId;
        }

        public String getDescription() {
            return analystDescription;
        }
    }
}
