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
import android.util.Log;

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
    private static final String KEY_REQUIRES_ANALYST_PASSWORD = "requires_analyst_password";
    private static final String KEY_REQUIRES_SIGNATURE = "requires_signature";
    private volatile TransferDatabase mTransferDatabase;
    private SharedPreferences mSharedPreferences;
    private Transfer mCurrentTransfer;
    private Analyst mCurrentAnalyst;
    private boolean mIsCurrentLocationAnalyst;
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

    public void showModalScannerDialog(Dialog dialog, final DialogInterface.OnDismissListener onDismissListener) {
        showDialog0(dialog, onDismissListener, null, true);
    }

    private void showDialog0(Dialog dialog, final DialogInterface.OnDismissListener onDismissListener, AbstractScanner.OnBarcodeScannedListener onBarcodeScannedListener, final boolean disableScanner) {
        if (dialog == null || mIsShowingDialog) {
            if (onDismissListener != null)
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
        return mSharedPreferences.getBoolean(KEY_REQUIRES_STARTUP_LOGIN, false);
    }

    public boolean setRequiresStartupLogin(boolean b) {
        return mSharedPreferences.edit().putBoolean(KEY_REQUIRES_STARTUP_LOGIN, b).commit();
    }

    public boolean requiresAnalystLogin() {
        return mSharedPreferences.getBoolean(KEY_REQUIRES_ANALYST_PASSWORD, true);
    }

    public boolean setRequiresAnalystLogin(boolean b) {
        return mSharedPreferences.edit().putBoolean(KEY_REQUIRES_ANALYST_PASSWORD, b).commit();
    }

    public boolean requiresSignature() {
        return mSharedPreferences.getBoolean(KEY_REQUIRES_SIGNATURE, true);
    }

    public boolean setRequiresSignature(boolean b) {
        return mSharedPreferences.edit().putBoolean(KEY_REQUIRES_SIGNATURE, b).commit();
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

    public Analyst getCurrentAnalyst() {
        return mCurrentAnalyst;
    }

    public boolean isAnalystBarcode(String barcode) {
        String barcodePrefix = BarcodeType.getLocationCustodyOf(barcode);
        return barcodePrefix != null && mTransferDatabase.select_count_from_analystNameTable_where_analystName_equals(barcodePrefix) > 0;
    }
    public boolean isCurrentLocationAnalyst() {
        return mIsCurrentLocationAnalyst;
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
        if (transfer != null && transfer.locationBarcode != null && BarcodeType.Location.isOfType(transfer.locationBarcode) && isAnalystBarcode(transfer.locationBarcode)) {
            mIsCurrentLocationAnalyst = true;
            mCurrentAnalyst = getAnalyst(BarcodeType.getLocationCustodyOf(transfer.locationBarcode));
        } else {
            mIsCurrentLocationAnalyst = false;
            mCurrentAnalyst = null;
        }
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
        this.mIsShowingDialog = showingDialog;
        updateScannerIsDisabled();
    }

    private void setIsShowingModalDialog(boolean showingModalDialog) {
        this.mIsShowingModalDialog = showingModalDialog;
        updateScannerIsDisabled();
    }

    private void setIsSaving(boolean isSaving) {
        if (isSaving) {
            pushOnBarcodeScannedListener(null);
        } else {
            popOnBarcodeScannedListener();
        }
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

    public void loadAnalysts() {
        /*
        //new WeakAsyncTask<>(readFileTaskListeners).execute();
        LineNumberReader lineReader = null;
        try {
            lineReader = new LineNumberReader(new FileReader(INPUT_FILE));
            String line;
            String[] elements;
            long currentLocationId = -1;

            mDatabase.beginTransaction();

            while ((line = lineReader.readLine()) != null) {
                elements = line.split("((?<!\\|)(\\|)(?!\\|))");

                for (int i = 0; i < elements.length; i++) {
                    elements[i] = elements[i].replaceAll("(^\")|(\"$)", "").replace("\"\"", "\"").replace("||", "|");
                }

                if (elements.length > 0) {
                    GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.bindString(1, elements[1]);
                    GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.bindString(1, elements[1]);
                    if (Location.isOfType(elements[1]) ? !(GET_DUPLICATES_OF_PRELOADED_LOCATION_BARCODE_STATEMENT.simpleQueryForLong() > 0) : !(GET_DUPLICATES_OF_PRELOADED_ITEM_BARCODE_STATEMENT.simpleQueryForLong() > 0)) {
                        if (elements.length == 3 && elements[0].equals("L")) {
                            //System.out.println("Location: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\'");
                            currentLocationId = addPreloadLocation(elements[1], elements[2]);
                            if (currentLocationId == -1)
                                throw new SQLException(String.format(Locale.US, "Error adding location \"%s\" from line %d", elements[1], lineReader.getLineNumber()));
                        } else if (elements.length == 4 && elements[0].equals(CASE_CONTAINER)) {
                            //System.out.println("Case-Container: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\', case-number = \'" + elements[3] + "\'");
                            if (addPreloadItem(currentLocationId, elements[1], elements[3], "", "", elements[2], ItemTable.ItemType.CASE_CONTAINER) < 0)
                                throw new SQLiteException(String.format(Locale.US, "Error adding case-container \"%s\" from line %d", elements[1], lineReader.getLineNumber()));
                        } else if (elements.length == 3 && elements[0].equals(BULK_CONTAINER)) {
                            //System.out.println("Bulk-Container: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\'");
                            if (addPreloadItem(currentLocationId, elements[1], "", "", "", elements[2], BULK_CONTAINER) < 0)
                                throw new SQLiteException(String.format(Locale.US, "Error adding bulk-container \"%s\" from line %d", elements[1], lineReader.getLineNumber()));
                        } else if (!BuildConfig.is_LAM_system && elements.length == 6 && elements[0].equals(ITEM)) {
                            //System.out.println("Item: barcode = \'" + elements[1] + "\', case-number = \'" + elements[2] + "\', item-number = \'" + elements[3] + "\', package = \'" + elements[4] + "\', description = \'" + elements[5] + "\'");
                            if (addPreloadItem(currentLocationId, elements[1], elements[2], elements[3], elements[4], elements[5], ItemTable.ItemType.ITEM) < 0)
                                throw new SQLiteException(String.format(Locale.US, "Error adding item \"%s\" from line %d", elements[1], lineReader.getLineNumber()));
                        } else if (BuildConfig.is_LAM_system && elements.length == 3 && elements[0].equals(CHEM_ITEM)) {
                            //System.out.println("Chem-Item: barcode = \'" + elements[1] + "\', description = \'" + elements[2] + "\'");
                            if (addPreloadItem(currentLocationId, elements[1], "", "", "", elements[2], ItemTable.ItemType.CHEM_ITEM) < 0)
                                throw new SQLiteException(String.format(Locale.US, "Error adding chem-item \"%s\" from line %d", elements[1], lineReader.getLineNumber()));
                        } else {
                            if ("L".equals(elements[0]) || CASE_CONTAINER.equals(elements[0]) || BULK_CONTAINER.equals(elements[0]) || (!BuildConfig.is_LAM_system && ITEM.equals(elements[0])) || (BuildConfig.is_LAM_system && CHEM_ITEM.equals(elements[0]))) {
                                throw new ParseException("Incorrect format or number of elements", lineReader.getLineNumber());
                            } else if (BuildConfig.is_LAM_system && ITEM.equals(elements[0])) {
                                throw new ParseException("LIMS item encountered in LAM preload data", lineReader.getLineNumber());
                            } else if (!BuildConfig.is_LAM_system && CHEM_ITEM.equals(elements[0])) {
                                throw new ParseException("LAM item encountered in LIMS preload data", lineReader.getLineNumber());
                            } else {
                                throw new ParseException(String.format("Identifier \"%s\" not recognised", elements[0]), lineReader.getLineNumber());
                            }
                        }
                    }
                } else if (lineReader.getLineNumber() < 2)
                    throw new ParseException("Blank file", lineReader.getLineNumber());
            }

            mDatabase.setTransactionSuccessful();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
            startActivity(new Intent(PreloadInventoryActivity.this, PreloadLocationsActivity.class));
            finish();
            toastShort("There was an error parsing the file");
        } finally {
            if (lineReader != null) {
                try {
                    lineReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (mDatabase.inTransaction())
                mDatabase.endTransaction();
        }
        */
    }

    public void signCurrentTransfer(final Context context, Bitmap bitmap, final Transfer transfer, final Utils.DetailedOnFinishListener onFinishListener) {
        final Utils.DetailedOnFinishListener temp = new Utils.DetailedOnFinishListener() {
            @Override
            public void onFinish(boolean success, String message) {
                if (success) {
                    success = mTransferDatabase.update_transferTable_set_signed_equalTo_where_id_equals(true, transfer.id) > 0;
                    if (success)
                        transfer.signed = true;
                    else
                        message += ": Error signing";
                }

                if (onFinishListener != null)
                    onFinishListener.onFinish(success, message);
                listenerReferences.remove(this);
            }
        };
        listenerReferences.add(temp);

        asyncSaveSignature(context, bitmap, transfer.id, temp);
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

    public void finalizeCurrentTransfer(final Activity activity, final Utils.OnProgressUpdateListener onProgressUpdateListener, final Utils.DetailedOnFinishListener onFinishListener) {
        setIsSaving(true);
        final Utils.DetailedOnFinishListener temp = new Utils.DetailedOnFinishListener() {
            @Override
            public void onFinish(boolean success, String message) {
                setIsSaving(false);
                if (success) {
                    success = mTransferDatabase.update_transferTable_set_finalized_equalTo_where_id_equals(true, getCurrentTransferId()) > 0;
                    if (success)
                        updateCurrentTransfer(getLastUnfinishedTransfer());
                    else
                        message += ": Error finalizing";
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
                    OUTPUT_FILE.setReadOnly();
                    OUTPUT_FILE.setWritable(true, true);
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

    private Analyst getAnalyst(String analystId) {
        if (analystId == null) return null;
        String analystIdSha1 = null;
        try {
            analystIdSha1 = Utils.SHA1(analystId);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "\"SHA-1\" algorithm not found");
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "\"US-ASCII\" charset not found");
            e.printStackTrace();
        }
        if (analystIdSha1 == null) return null;

        final Cursor cursor = mTransferDatabase.getDatabase().query(TransferDatabase.AnalystTable.NAME, new String[] { TransferDatabase.Key.ID, TransferDatabase.Key.ANALYST_ID_SHA_1, TransferDatabase.Key.ANALYST_PASSWORD_SHA_1, TransferDatabase.Key.ANALYST_DESCRIPTION }, TransferDatabase.Key.ANALYST_ID_SHA_1 + " = ?", new String[] { analystIdSha1 }, null, null, null, "1");
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        Analyst temp = new Analyst(cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.ID)), analystId, cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.ANALYST_PASSWORD_SHA_1)), cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.ANALYST_DESCRIPTION)));
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
        public final String passwordSha1;
        public final String analystDescription;

        private Analyst(long id, String analystId, String passwordSha1, String analystDescription) {
            this.id = id;
            this.analystId = analystId;
            this.passwordSha1 = passwordSha1;
            this.analystDescription = analystDescription;
        }

        public boolean verifyAnalystLogin(String password) {
            try {
                return passwordSha1.equals(Utils.SHA1(analystId + password));
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
