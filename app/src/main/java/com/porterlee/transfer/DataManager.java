package com.porterlee.transfer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.porterlee.transfer.Utils.Batch;
import com.porterlee.transfer.Utils.Item;
import com.porterlee.transfer.Utils.Transfer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private Batch mCurrentBatch;
    private Transfer mCurrentTransfer;
    private Runnable mOnCurrentBatchChangedListener;
    private Runnable mOnCurrentTransferChangedListener;
    private boolean mIsSaving;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private ArrayList<Object> listenerReferences = new ArrayList<>();

    public DataManager(SharedPreferences sharedPreferences) {
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

    public boolean isSaving() {
        return mIsSaving;
    }

    public void init(Context context) {
        mTransferDatabase = new TransferDatabase(context);
        setCurrentBatch(query_getLastBatch());
        setCurrentTransfer(query_getLastActiveTransferWithBatchId(getCurrentBatchId()));

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
        if (!cursor.moveToFirst())
            return null;
        Item temp = constructItemFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Transfer query_getTransfer(long id) {
        final Cursor cursor = mTransferDatabase.query_getTransfer(id);
        if (!cursor.moveToFirst())
            return null;
        Transfer temp = constructTransferFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Transfer query_getLastTransfer() {
        final Cursor cursor = mTransferDatabase.query_getLastTransfer();
        if (!cursor.moveToFirst())
            return null;
        Transfer temp = constructTransferFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Transfer query_getLastActiveTransfer() {
        final Cursor cursor = mTransferDatabase.query_getLastActiveTransfer();
        if (!cursor.moveToFirst())
            return null;
        Transfer temp = constructTransferFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Transfer query_getLastTransferWithBatchId(long batchId) {
        if (batchId < 0)
            return null;
        final Cursor cursor = mTransferDatabase.query_getLastTransferWithBatchId(batchId);
        if (!cursor.moveToFirst())
            return null;
        Transfer temp = constructTransferFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Transfer query_getLastActiveTransferWithBatchId(long batchId) {
        if (batchId < 0)
            return null;
        final Cursor cursor = mTransferDatabase.query_getLastActiveTransferWithBatchId(batchId);
        if (!cursor.moveToFirst())
            return null;
        Transfer temp = constructTransferFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Batch query_getBatch(long id) {
        final Cursor cursor = mTransferDatabase.query_getBatch(id);
        if (!cursor.moveToFirst())
            return null;
        Batch temp = constructBatchFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private Batch query_getLastBatch() {
        final Cursor cursor = mTransferDatabase.query_getLastBatch();
        if (!cursor.moveToFirst())
            return null;
        Batch temp = constructBatchFromCursor(cursor);
        cursor.close();
        return temp;
    }

    private long query_getLastBatchID() {
        if (mTransferDatabase.query_getBatchCount() <= 0) {
            return mTransferDatabase.query_getLastBatchId();
        } else {
            return -1;
        }
    }

    public long query_getFinalTransferCount() {
        return mTransferDatabase.query_getFinalTransferCountWithBatchId(getCurrentBatchId());
    }


    public boolean query_updateTransferSetComments(String comments) {
        return mTransferDatabase.query_updateTransferSetComments(getCurrentTransferId(), comments) > 0;
    }

    public Batch getCurrentBatch() {
        return mCurrentBatch;
    }

    public Transfer getCurrentTransfer() {
        return mCurrentTransfer;
    }

    public boolean query_updateCurrentTransferSetCanceled() {
        final boolean success = mTransferDatabase.query_updateTransferSetCanceled(getCurrentTransferId()) > 0;
        if (success) {
            setCurrentTransfer(query_getLastActiveTransferWithBatchId(getCurrentBatchId()));
        }
        return success;
    }

    public boolean query_updateCurrentTransferSetFinalized() {
        final boolean success = mTransferDatabase.query_updateTransferSetFinalized(getCurrentTransferId()) > 0;
        if (success) {
            setCurrentTransfer(query_getLastActiveTransferWithBatchId(getCurrentBatchId()));
        }
        return success;
    }

    private void setCurrentTransfer(@Nullable Transfer transfer) {
        if (transfer != null)
            setCurrentBatch(query_getBatch(transfer.batch_id));
        mCurrentTransfer = transfer;
        if (mOnCurrentTransferChangedListener != null)
            mOnCurrentTransferChangedListener.run();
    }

    private void setCurrentBatch(@Nullable Batch batch) {
        mCurrentBatch = batch;
        if (getCurrentTransfer() != null && getCurrentTransfer().batch_id != getCurrentBatchId())
            setCurrentTransfer(query_getLastActiveTransferWithBatchId(getCurrentBatchId()));
        if (mOnCurrentBatchChangedListener != null)
            mOnCurrentBatchChangedListener.run();
    }

    public void switchToPreviousTransfer() {
        final long batchId = getCurrentBatchId();
        final long transferId = getCurrentTransferId();
        if (transferId >= 0) {
            if (mTransferDatabase.query_getPreviousTransferCountWithBatchId(batchId, transferId) > 0) {
                Cursor cursor = mTransferDatabase.query_getPreviousTransferWithBatchId(batchId, transferId);
                setCurrentTransfer(cursor.moveToFirst() ? constructTransferFromCursor(cursor) : null);
            } else {
                // no previous transfer
            }
        } else {
            setCurrentTransfer(query_getLastTransferWithBatchId(getCurrentBatchId()));
        }
    }

    public void switchToNextTransfer() {
        final long batchId = getCurrentBatchId();
        final long transferId = getCurrentTransferId();
        if (transferId >= 0) {
            if (mTransferDatabase.query_getNextTransferCountWithBatchId(batchId, transferId) > 0) {
                Cursor cursor = mTransferDatabase.query_getNextTransferWithBatchId(batchId, transferId);
                setCurrentTransfer(cursor.moveToFirst() ? constructTransferFromCursor(cursor) : null);
            } else {
                setCurrentTransfer(null); // possible to start a new transfer now
            }
        }
    }

    public boolean query_hasPreviousTransfer() {
        final long batchId = getCurrentBatchId();
        final long transferId = getCurrentTransferId();
        if (transferId >= 0) {
            if (mTransferDatabase.query_getPreviousTransferCountWithBatchId(batchId, transferId) > 0) {
                return true;
            } else {
                return false; // no previous transfer
            }
        }
        return mTransferDatabase.query_getTransferCountWithBatchId(batchId) > 0;
    }

    public boolean query_hasNextTransfer() {
        final long batchId = getCurrentBatchId();
        final long transferId = getCurrentTransferId();
        if (transferId >= 0) {
            if (mTransferDatabase.query_getNextTransferCountWithBatchId(batchId, transferId) > 0) {
                return true;
            } else {
                return true; // next transfer is null
            }
        }
        return false;
    }

    public boolean query_hasActiveTransfer() {
        final long batchId = getCurrentBatchId();
        return batchId >= 0 && mTransferDatabase.query_getActiveTransferCountWithBatchId(batchId) > 0;
    }

    public boolean query_isCurrentTransferActive() {
        final long transferId = getCurrentTransferId();
        return transferId >= 0 && mTransferDatabase.query_getActiveTransferCountWithTransferId(transferId) > 0;
    }

    public long getCurrentTransferId() {
        return mCurrentTransfer != null ? mCurrentTransfer.id : -1;
    }

    public long getCurrentBatchId() {
        return mCurrentBatch != null ? mCurrentBatch.id : -1;
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

    /**
     * Inserts a row into the {@link TransferDatabase} and sets the current transfer to a
     * {@link Transfer} object representing it
     *
     * @param locationBarcode the barcode of the location to be added to the database
     */
    public long startNewTransfer(@NonNull String locationBarcode) {
        query_insertBatchIfNotExist();

        long transferId = mTransferDatabase.query_insertTransfer(mTransferDatabase.query_getLastBatchId(), locationBarcode);
        if (transferId < 0)
            throw new RuntimeException("could not create new transfer");
        setCurrentTransfer(query_getTransfer(transferId));

        return transferId;
    }

    public long startNewBatch() {
        long batchId = query_insertBatch();
        if (batchId < 0)
            throw new RuntimeException("could not create new batch");
        setCurrentBatch(query_getBatch(batchId));

        return batchId;
    }

    public void signCurrentTransfer(final Context context, @Nullable final String name, Bitmap bitmap, final Utils.DetailedOnFinishListener onFinishListener) {
        final Transfer transfer = getCurrentTransfer();
        final Utils.DetailedOnFinishListener temp = new Utils.DetailedOnFinishListener() {
            @Override
            public void onFinish(boolean success, String message) {
                if (success) {
                    success = mTransferDatabase.query_updateTransferSetSigned(transfer.id, name) > 0;
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

    public long query_insertBatchIfNotExist() {
        if (mTransferDatabase.query_getBatchCount() > 0)
            return -1;

        return query_insertBatch();
    }

    public long query_insertBatch() {
        long batchId = mTransferDatabase.query_insertBatch();

        if (batchId < 0)
            throw new RuntimeException("could not create new batch");

        return batchId;
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

        try (FileReader fileReader = new FileReader(file);
             BufferedReader reader = new BufferedReader(fileReader)) {
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
                            null,
                            null
                    ));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }

        return transfers;
    }

    public void saveBatch(final long batchId, final Activity activity, final Utils.OnProgressUpdateListener onProgressUpdateListener, final Utils.DetailedOnFinishListener onFinishListener) {
        mIsSaving = true;
        getScannerUtils().pushEnabledState(false);
        final Utils.DetailedOnFinishListener temp = new Utils.DetailedOnFinishListener() {
            @Override
            public void onFinish(boolean success, String message) {
                getScannerUtils().popEnabledState();
                mIsSaving = false;
                onFinishListener.onFinish(success, message);
                listenerReferences.remove(this);
                listenerReferences.remove(onFinishListener);
                listenerReferences.remove(onProgressUpdateListener);
            }
        };
        listenerReferences.add(temp);
        listenerReferences.add(onFinishListener);
        listenerReferences.add(onProgressUpdateListener);
        asyncSaveBatchToFile(activity, mTransferDatabase, batchId, onProgressUpdateListener, temp);
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
                final long batchItemCount;
                long cumulativeItemCount = 0;

                Cursor transferCursor = null;
                Cursor itemCursor = null;
                {
                    TransferDatabase tmp_transferDatabase = transferDatabase_weak.get();
                    if (tmp_transferDatabase != null) {
                        batchItemCount = tmp_transferDatabase.query_getItemCountFromFinalTransfersWithBatchId(batchId);
                        transferCursor = tmp_transferDatabase.query_getFinalTransfersWithBatchId(batchId);
                    } else {
                        return;
                    }
                }

                int transferIdIndex = transferCursor.getColumnIndexOrThrow(TransferDatabase.Key.ID);
                int transferLocationBarcodeIndex = transferCursor.getColumnIndexOrThrow(TransferDatabase.Key.LOCATION_BARCODE);
                int transferStartDateTimeIndex = transferCursor.getColumnIndexOrThrow(TransferDatabase.Key.START_DATETIME);

                transferCursor.moveToFirst();
                OUTPUT_FILE.setWritable(true, true);

                try (FileOutputStream fileOutputStream = new FileOutputStream(OUTPUT_FILE);
                     // todo: try to guess size
                     BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream);
                     PrintStream printStream = new PrintStream(outputStream)) {
                    printStream.print(OUTPUT_FILE_HEADER + "\r\n");
                    printStream.flush();

                    while (!transferCursor.isAfterLast()) {
                        {
                            TransferDatabase tmp_transferDatabase = transferDatabase_weak.get();
                            if (tmp_transferDatabase != null) {
                                if (itemCursor != null)
                                    itemCursor.close();
                                itemCursor = tmp_transferDatabase.query_getItemsWithTransferId(transferCursor.getLong(transferIdIndex));
                            } else {
                                return;
                            }
                        }
                        itemCursor.moveToFirst();
                        int itemBarcodeIndex = itemCursor.getColumnIndexOrThrow(TransferDatabase.Key.BARCODE);
                        int itemQuantityIndex = itemCursor.getColumnIndexOrThrow(TransferDatabase.Key.QUANTITY);
                        int itemDateTimeIndex = itemCursor.getColumnIndexOrThrow(TransferDatabase.Key.SCAN_DATETIME);

                        final int MAX_UPDATE_COUNT = 100;
                        int updateCount = 0;

                        printStream.printf("\"%s\"|\"%d\"|\"%s\"\r\n", transferCursor.getString(transferLocationBarcodeIndex).replace("\"", "\"\""), transferCursor.getLong(transferIdIndex), transferCursor.getString(transferStartDateTimeIndex).replace("-", "/"));

                        while (!itemCursor.isAfterLast()) {
                            {
                                Utils.OnProgressUpdateListener tmp_onProgressUpdateListener = onProgressUpdateListener_weak.get();
                                if (tmp_onProgressUpdateListener != null) {
                                    final float tempProgress = ((float) (cumulativeItemCount + itemCursor.getPosition())) / batchItemCount;
                                    if ((tempProgress * MAX_UPDATE_COUNT) > updateCount) {
                                        tmp_onProgressUpdateListener.onProgressUpdate(tempProgress);
                                        updateCount++;
                                    }
                                }
                            }

                            if (BuildConfig.ui_enableQuantityEdit) {
                                printStream.printf("\"%s\"|\"%d\"|\"%d\"|\"%s\"\r\n", itemCursor.getString(itemBarcodeIndex).replace("\"", "\"\""), itemCursor.getLong(itemQuantityIndex), transferCursor.getLong(transferIdIndex), itemCursor.getString(itemDateTimeIndex).replace("-", "/"));
                            } else {
                                printStream.printf("\"%s\"|\"%d\"|\"%s\"\r\n", itemCursor.getString(itemBarcodeIndex).replace("\"", "\"\""), transferCursor.getLong(transferIdIndex), itemCursor.getString(itemDateTimeIndex).replace("-", "/"));
                            }

                            itemCursor.moveToNext();
                        }

                        cumulativeItemCount += itemCursor.getCount();
                        transferCursor.moveToNext();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    {
                        Utils.DetailedOnFinishListener tmp_onFinishListener = onFinishListener_weak.get();
                        if (tmp_onFinishListener != null)
                            tmp_onFinishListener.onFinish(false, e.getMessage());
                    }
                    return;
                } finally {
                    if (transferCursor != null)
                        transferCursor.close();
                    if (itemCursor != null)
                        itemCursor.close();
                }

                {
                    Context tmp_context = context_weak.get();
                    if (tmp_context != null)
                        Utils.refreshExternalFile(tmp_context, OUTPUT_FILE);
                }

                {
                    Utils.DetailedOnFinishListener tmp_onFinishListener = onFinishListener_weak.get();
                    if (tmp_onFinishListener != null)
                        tmp_onFinishListener.onFinish(true, "Saved");
                }
            }
        });
    }

    public static void asyncSaveBatchToFileJSON(Activity activity, TransferDatabase transferDatabase, final long batchId, Utils.OnProgressUpdateListener onProgressUpdateListener, Utils.DetailedOnFinishListener onFinishListener) {
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
                final long batchItemCount;
                long cumulativeItemCount = 0;

                Cursor transferCursor;
                Cursor itemCursor = null;
                {
                    TransferDatabase tmp_transferDatabase = transferDatabase_weak.get();
                    if (tmp_transferDatabase != null) {
                        batchItemCount = tmp_transferDatabase.query_getItemCountFromFinalTransfersWithBatchId(batchId);
                        transferCursor = tmp_transferDatabase.query_getFinalTransfersWithBatchId(batchId);
                    } else {
                        return;
                    }
                }

                final int transferIdIndex = transferCursor.getColumnIndexOrThrow(TransferDatabase.Key.ID);
                final int transferLocationBarcodeIndex = transferCursor.getColumnIndexOrThrow(TransferDatabase.Key.LOCATION_BARCODE);
                final int transferSignedIndex = transferCursor.getColumnIndexOrThrow(TransferDatabase.Key.SIGNED);
                final int transferStartDateTimeIndex = transferCursor.getColumnIndexOrThrow(TransferDatabase.Key.START_DATETIME);
                final int transferSignDateTimeIndex = transferCursor.getColumnIndexOrThrow(TransferDatabase.Key.SIGN_DATETIME);
                final int transferCommentsIndex = transferCursor.getColumnIndexOrThrow(TransferDatabase.Key.COMMENTS);
                final int transferSigneeNameIndex = transferCursor.getColumnIndexOrThrow(TransferDatabase.Key.SIGNEE_NAME);

                transferCursor.moveToFirst();

                try {
                    JSONObject outputObject = new JSONObject();
                    outputObject.put("applicationId", BuildConfig.APPLICATION_ID);
                    {
                        JSONObject flavorObject = new JSONObject();
                        flavorObject.put("system", BuildConfig.FLAVOR_system);
                        flavorObject.put("scannerSdk", BuildConfig.FLAVOR_scannerSdk);
                        outputObject.put("flavor", flavorObject);
                    }
                    outputObject.put("buildType", BuildConfig.BUILD_TYPE);
                    outputObject.put("versionName", BuildConfig.VERSION_NAME);
                    outputObject.put("versionCode", BuildConfig.VERSION_CODE);

                    JSONObject batchObject = new JSONObject();
                    {
                        batchObject.put("id", batchId);
                        JSONArray transferArray = new JSONArray();

                        while (!transferCursor.isAfterLast()) {
                            JSONObject transferObject = new JSONObject();
                            transferObject.put("id", transferCursor.getLong(transferIdIndex));
                            transferObject.put("location_barcode", transferCursor.getString(transferLocationBarcodeIndex));
                            transferObject.put("start_datetime", transferCursor.getString(transferStartDateTimeIndex));
                            transferObject.put("comments", transferCursor.isNull(transferCommentsIndex) ? null : transferCursor.getString(transferCommentsIndex));

                            boolean signed = transferCursor.getLong(transferSignedIndex) != 0;
                            transferObject.put("signed", signed);
                            if (signed) {
                                transferObject.put("sign_datetime", transferCursor.isNull(transferSignDateTimeIndex) ? null : transferCursor.getString(transferSignDateTimeIndex));
                                transferObject.put("signee_name", transferCursor.isNull(transferSigneeNameIndex) ? null : transferCursor.getString(transferSigneeNameIndex));
                            }

                            {
                                TransferDatabase tmp_transferDatabase = transferDatabase_weak.get();
                                if (tmp_transferDatabase != null) {
                                    if (itemCursor != null) itemCursor.close();
                                    itemCursor = tmp_transferDatabase.query_getItemsWithTransferId(transferCursor.getLong(transferIdIndex));
                                } else {
                                    return;
                                }
                            }

                            //final int itemIdIndex = itemCursor.getColumnIndexOrThrow(TransferDatabase.Key.ID);
                            final int itemBarcodeIndex = itemCursor.getColumnIndexOrThrow(TransferDatabase.Key.BARCODE);
                            final int itemQuantityIndex = itemCursor.getColumnIndexOrThrow(TransferDatabase.Key.QUANTITY);
                            final int itemScanDateTimeIndex = itemCursor.getColumnIndexOrThrow(TransferDatabase.Key.SCAN_DATETIME);

                            itemCursor.moveToFirst();

                            {
                                JSONArray itemArray = new JSONArray();

                                final int MAX_UPDATE_COUNT = 100;
                                int updateCount = 0;

                                while (!itemCursor.isAfterLast()) {
                                    JSONObject itemObject = new JSONObject();

                                    {
                                        Utils.OnProgressUpdateListener tmp_onProgressUpdateListener = onProgressUpdateListener_weak.get();
                                        if (tmp_onProgressUpdateListener != null) {
                                            final float tempProgress = ((float) (cumulativeItemCount + itemCursor.getPosition())) / batchItemCount;
                                            if ((tempProgress * MAX_UPDATE_COUNT) > updateCount) {
                                                tmp_onProgressUpdateListener.onProgressUpdate(tempProgress);
                                                updateCount++;
                                            }
                                        }
                                    }

                                    //itemObject.put("id", itemCursor.getLong(itemIdIndex));
                                    itemObject.put("barcode", itemCursor.getString(itemBarcodeIndex));
                                    itemObject.put("quantity", itemCursor.getLong(itemQuantityIndex));
                                    itemObject.put("scan_datetime", itemCursor.getString(itemScanDateTimeIndex));

                                    itemArray.put(itemObject);

                                    itemCursor.moveToNext();
                                }
                                transferObject.put("items", itemArray);
                            }
                            transferArray.put(transferObject);

                            cumulativeItemCount += itemCursor.getCount();
                            transferCursor.moveToNext();
                        }
                        batchObject.put("transfers", transferArray);
                    }
                    outputObject.put("batch", batchObject);

                    OUTPUT_FILE.setWritable(true, true);
                    try (FileOutputStream fileOutputStream = new FileOutputStream(OUTPUT_FILE);
                         // todo: try to guess size
                         BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream);
                         PrintStream printStream = new PrintStream(outputStream)) {
                        printStream.print(outputObject);
                    }
                } catch (JSONException | IOException e) {
                    e.printStackTrace();
                    {
                        Utils.DetailedOnFinishListener tmp_onFinishListener = onFinishListener_weak.get();
                        if (tmp_onFinishListener != null)
                            tmp_onFinishListener.onFinish(false, e.getMessage());
                    }
                    return;
                } finally {
                    if (transferCursor != null)
                        transferCursor.close();
                    if (itemCursor != null)
                        itemCursor.close();
                }

                {
                    Context tmp_context = context_weak.get();
                    if (tmp_context != null)
                        Utils.refreshExternalFile(tmp_context, OUTPUT_FILE);
                }

                {
                    Utils.DetailedOnFinishListener tmp_onFinishListener = onFinishListener_weak.get();
                    if (tmp_onFinishListener != null)
                        tmp_onFinishListener.onFinish(true, "Saved");
                }
            }
        });
    }
}
