package com.porterlee.transfer;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;

public final class TransferDatabase extends SQLiteOpenHelper {
    public static final String TAG = TransferDatabase.class.getCanonicalName();
    public static final String OLD_DATABASE_NAME = "lims_transfer";
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "plc_transfer.db";

    private final Context mContext;
    public ItemTable items = new ItemTable();
    public TransferTable transfers = new TransferTable();
    public BatchTable batches = new BatchTable();

    public TransferDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context;
        getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String[] databaseList = mContext.databaseList();
        boolean oldDatabaseExists = false;
        if (databaseList != null) {
            for (String database : databaseList) {
                if (OLD_DATABASE_NAME.equals(database)) {
                    oldDatabaseExists = true;
                    break;
                }
            }
        }

        db.execSQL(ItemTable.SQL_CREATE_TABLE);
        db.execSQL(TransferTable.SQL_CREATE_TABLE);
        db.execSQL(BatchTable.SQL_CREATE_TABLE);

        if (oldDatabaseExists) {
            db.setTransactionSuccessful();
            db.endTransaction(); // end transaction so we can attach the old database

            db.execSQL("ATTACH DATABASE '" + mContext.getDatabasePath(OLD_DATABASE_NAME).toString() + "' AS old_db");

            db.beginTransaction();

            {
                db.compileStatement("INSERT INTO items ( _id, transfer_id, barcode, quantity, scan_datetime ) SELECT _id, transfer_id, barcode, quantity, date_time AS scan_datetime FROM old_db.items ORDER BY _id").executeInsert();

                long oldItemCount = db.compileStatement("SELECT COUNT(*) FROM old_db.items").simpleQueryForLong();
                long newItemCount = db.compileStatement("SELECT COUNT(*) FROM items").simpleQueryForLong();
                if (oldItemCount != newItemCount)
                    throw new UpgradeException("could not copy item table from previous database: COUNT(old_db.items)=" + oldItemCount + " COUNT(items)=" + newItemCount);
            }

            {
                db.compileStatement("INSERT INTO transfers ( _id, batch_id, location_barcode, signed, finalized, canceled, saved, start_datetime, sign_datetime, finalize_datetime ) SELECT _id, NULL AS batch_id, location_barcode, signed, finalized, canceled, NULL AS saved, start_date_time AS start_datetime, sign_date_time AS sign_datetime, finalize_date_time AS finalize_datetime FROM old_db.transfers ORDER BY _id").executeInsert();

                long oldTransferCount = db.compileStatement("SELECT COUNT(*) FROM old_db.transfers").simpleQueryForLong();
                long newTransferCount = db.compileStatement("SELECT COUNT(*) FROM transfers").simpleQueryForLong();
                if (oldTransferCount != newTransferCount)
                    throw new UpgradeException("could not copy transfer table from previous database");
            }

            db.setTransactionSuccessful();
            db.endTransaction();

            db.execSQL("DETACH DATABASE old_db");
            // todo uncomment
            //mContext.deleteDatabase(OLD_DATABASE_NAME);
            File oldDatabaseFile = mContext.getDatabasePath(OLD_DATABASE_NAME);
            //noinspection ResultOfMethodCallIgnored
            oldDatabaseFile.renameTo(new File(oldDatabaseFile.getParentFile(), OLD_DATABASE_NAME + ".old"));

            db.beginTransaction();
        }

        db.execSQL(ItemTable.SQL_CREATE_INDEX_TRANSFER_ID);
        db.execSQL(ItemTable.SQL_CREATE_INDEX_BARCODE);
        db.execSQL(TransferTable.SQL_CREATE_INDEX_FINALIZED_CANCELED);
        db.execSQL(TransferTable.SQL_CREATE_INDEX_LOCATION_BARCODE);
        db.execSQL(TransferTable.SQL_CREATE_INDEX_BATCH_ID);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == newVersion) return;
        if (oldVersion > newVersion)
            throw new UpgradeException("database version (" + oldVersion + ") is greater than target version (" + newVersion + ")!");
        switch (oldVersion) {
            case 0:
                break;
            default:
                throw new UpgradeException("unknown database version: " + oldVersion);
        }
    }

    public boolean delete(@NonNull Context context) {
        close();
        return context.deleteDatabase(DATABASE_NAME);
    }

    public boolean exists(@NonNull Context context) {
        for (String name : context.databaseList())
            if (DATABASE_NAME.equals(name))
                return true;

        return false;
    }

    private SQLiteStatement mQuery_getItemCount = null;
    public synchronized long query_getItemCount() {
        if (mQuery_getItemCount == null)
            mQuery_getItemCount = getReadableDatabase().compileStatement("SELECT COUNT(*) FROM items");
        return mQuery_getItemCount.simpleQueryForLong();
    }

    private SQLiteStatement mQuery_getItemCountWithTransferId = null;
    public synchronized long query_getItemCountWithTransferId(long transferId) {
        if (mQuery_getItemCountWithTransferId == null)
            mQuery_getItemCountWithTransferId = getReadableDatabase().compileStatement("SELECT COUNT(*) FROM items WHERE transfer_id = ?");
        mQuery_getItemCountWithTransferId.bindLong(1, transferId);
        return mQuery_getItemCountWithTransferId.simpleQueryForLong();
    }

    private SQLiteStatement mQuery_getItemCountWithBarcodeAndTransferId = null;
    public synchronized long query_getItemCountWithTransferIdAndBarcode(long transferId, String barcode) {
        if (mQuery_getItemCountWithBarcodeAndTransferId == null)
            mQuery_getItemCountWithBarcodeAndTransferId = getReadableDatabase().compileStatement("SELECT COUNT(*) FROM items WHERE transfer_id = ? AND barcode = ?");
        mQuery_getItemCountWithBarcodeAndTransferId.bindLong(1, transferId);
        mQuery_getItemCountWithBarcodeAndTransferId.bindString(2, barcode);
        return mQuery_getItemCountWithBarcodeAndTransferId.simpleQueryForLong();
    }

    private SQLiteStatement mQuery_insertItemWithTransferId = null;
    public synchronized long query_insertItem(long transferId, String barcode, long quantity) {
        if (mQuery_insertItemWithTransferId == null)
            mQuery_insertItemWithTransferId = getWritableDatabase().compileStatement("INSERT INTO items ( transfer_id, barcode, quantity, scan_datetime ) VALUES ( ?, ?, ?, datetime('now', 'localtime') )");
        mQuery_insertItemWithTransferId.bindLong(1, transferId);
        mQuery_insertItemWithTransferId.bindString(2, barcode);
        mQuery_insertItemWithTransferId.bindLong(3, quantity);
        return mQuery_insertItemWithTransferId.executeInsert();
    }
    /*
        private SQLiteStatement mQuery_getItemCountWithBatchId = null;
        public synchronized long query_getItemCountWithBatchId(long batchId) {
            // todo: check
            if (mQuery_getItemCountWithBatchId == null)
                mQuery_getItemCountWithBatchId = getReadableDatabase().compileStatement("SELECT COUNT(*) FROM items WHERE transfer_id IN ( SELECT _id FROM transfers WHERE batch_id = ? )");
            mQuery_getItemCountWithBatchId.bindLong(1, batchId);
            return mQuery_getItemCountWithBatchId.simpleQueryForLong();
        }
    */
    private SQLiteStatement mQuery_updateItemQuantity = null;
    public synchronized long query_updateItemQuantity(long id, int quantity) {
        if (mQuery_updateItemQuantity == null)
            mQuery_updateItemQuantity = getWritableDatabase().compileStatement("UPDATE items SET quantity = ? WHERE _id = ?");
        mQuery_updateItemQuantity.bindLong(1, quantity);
        mQuery_updateItemQuantity.bindLong(2, id);
        return mQuery_updateItemQuantity.executeUpdateDelete();
    }

    private SQLiteStatement mQuery_deleteItem = null;
    public synchronized long query_deleteItem(long id) {
        if (mQuery_deleteItem == null)
            mQuery_deleteItem = getWritableDatabase().compileStatement("DELETE FROM items WHERE _id = ?");
        mQuery_deleteItem.bindLong(1, id);
        return mQuery_deleteItem.executeUpdateDelete();
    }

    private SQLiteStatement mQuery_getTransferCount = null;
    public synchronized long query_getTransferCount() {
        if (mQuery_getTransferCount == null)
            mQuery_getTransferCount = getReadableDatabase().compileStatement("SELECT COUNT(*) FROM transfers");
        return mQuery_getTransferCount.simpleQueryForLong();
    }

    private SQLiteStatement mQuery_getActiveTransferCount = null;
    public synchronized long query_getActiveTransferCount() {
        if (mQuery_getActiveTransferCount == null)
            mQuery_getActiveTransferCount = getReadableDatabase().compileStatement("SELECT COUNT(*) FROM transfers WHERE finalized = 0 AND canceled = 0");
        return mQuery_getActiveTransferCount.simpleQueryForLong();
    }

    private SQLiteStatement mQuery_getPreviousTransferCount = null;
    public synchronized long query_getPreviousTransferCount(long id) {
        if (mQuery_getPreviousTransferCount == null)
            mQuery_getPreviousTransferCount = getReadableDatabase().compileStatement("SELECT COUNT(*) FROM transfers WHERE _id < ?");
        mQuery_getPreviousTransferCount.bindLong(1, id);
        return mQuery_getPreviousTransferCount.simpleQueryForLong();
    }

    private SQLiteStatement mQuery_getNextTransferCount = null;
    public synchronized long query_getNextTransferCount(long id) {
        if (mQuery_getNextTransferCount == null)
            mQuery_getNextTransferCount = getReadableDatabase().compileStatement("SELECT COUNT(*) FROM transfers WHERE _id > ?");
        mQuery_getNextTransferCount.bindLong(1, id);
        return mQuery_getNextTransferCount.simpleQueryForLong();
    }

    private SQLiteStatement mQuery_insertTransfer = null;
    public synchronized long query_insertTransfer(long batchId, String locationBarcode) {
        if (mQuery_insertTransfer == null)
            mQuery_insertTransfer = getWritableDatabase().compileStatement("INSERT INTO transfers ( batch_id, location_barcode, start_datetime ) VALUES ( ?, ?, datetime('now', 'localtime') )");
        mQuery_insertTransfer.bindLong(1, batchId);
        mQuery_insertTransfer.bindString(2, locationBarcode);
        return mQuery_insertTransfer.executeInsert();
    }
/*
    private SQLiteStatement mQuery_updateTransferSetBatchId1 = null;
    public synchronized long query_updateTransferSetBatchId(long id, long batchId) {
        // todo: check
        if (mQuery_updateTransferSetBatchId1 == null)
            mQuery_updateTransferSetBatchId1 = getWritableDatabase().compileStatement("UPDATE transfers SET batch_id = ? WHERE _id = ? AND batch_id = NULL");
        mQuery_updateTransferSetBatchId1.bindLong(1, batchId);
        mQuery_updateTransferSetBatchId1.bindLong(2, id);
        return mQuery_updateTransferSetBatchId1.executeUpdateDelete();
    }
*/
/*
    private SQLiteStatement mQuery_updateTransferSetBatchId2 = null;
    public synchronized long query_updateTransferSetBatchId(long id, String barcode, long batchId) {
        // todo: check
        if (mQuery_updateTransferSetBatchId2 == null)
            mQuery_updateTransferSetBatchId2 = getWritableDatabase().compileStatement("UPDATE transfers SET batch_id = ? WHERE _id = ? AND batch_id = NULL AND location_barcode = ?");
        mQuery_updateTransferSetBatchId2.bindLong(1, batchId);
        mQuery_updateTransferSetBatchId2.bindLong(2, id);
        mQuery_updateTransferSetBatchId2.bindString(3, barcode);
        return mQuery_updateTransferSetBatchId2.executeUpdateDelete();
    }
*/
    private SQLiteStatement mQuery_updateTransferSetBatchId = null;
    public synchronized long query_updateTransfersSetBatchId(ArrayList<Utils.Transfer> transfers, long batchId) {
        // todo: test
        if (mQuery_updateTransferSetBatchId == null)
            mQuery_updateTransferSetBatchId = getWritableDatabase().compileStatement("UPDATE transfers SET batch_id = ? WHERE _id = ? AND batch_id = NULL AND location_barcode = ? AND start_datetime = ?");

        getWritableDatabase().beginTransaction();

        try {
            for (Utils.Transfer transfer : transfers) {
                if (transfer == null)
                    throw new UpgradeException("transfer is null");
                mQuery_updateTransferSetBatchId.bindLong(1, batchId);
                mQuery_updateTransferSetBatchId.bindLong(2, transfer.id);
                mQuery_updateTransferSetBatchId.bindString(3, transfer.location_barcode);
                mQuery_updateTransferSetBatchId.bindString(4, transfer.start_datetime);

                if (mQuery_updateTransferSetBatchId.executeUpdateDelete() <= 0) {
                    throw new UpgradeException("update was unsuccessful: " + transfer);
                }
            }
            getWritableDatabase().setTransactionSuccessful();
            Log.v(TAG, "query_updateTransfersSetBatchId() - success");
        } catch (UpgradeException | NullPointerException e) {
            Log.w(TAG, "query_updateTransfersSetBatchId() - fail");
            e.printStackTrace();
        } finally {
            getWritableDatabase().endTransaction();
        }

        return mQuery_updateTransferSetBatchId.executeUpdateDelete();
    }

    private SQLiteStatement mQuery_updateTransferSigned = null;
    public synchronized long query_updateTransferSetSigned(long id) {
        // todo: test
        if (mQuery_updateTransferSigned == null)
            mQuery_updateTransferSigned = getWritableDatabase().compileStatement("UPDATE transfers SET signed = 1, sign_datetime = datetime('now', 'localtime') WHERE finalized == 0 AND canceled == 0 AND _id = ?");
        mQuery_updateTransferSigned.bindLong(1, id);
        return mQuery_updateTransferSigned.executeUpdateDelete();
    }

    private SQLiteStatement mQuery_updateTransferFinalized = null;
    public synchronized long query_updateTransferSetFinalized(long id) {
        if (mQuery_updateTransferFinalized == null)
            mQuery_updateTransferFinalized = getWritableDatabase().compileStatement("UPDATE transfers SET finalized = 1, finalize_datetime = datetime('now', 'localtime') WHERE canceled == 0 AND _id = ?");
        mQuery_updateTransferFinalized.bindLong(1, id);
        return mQuery_updateTransferFinalized.executeUpdateDelete();
    }

    private SQLiteStatement mQuery_updateTransferCanceled = null;
    public synchronized long query_updateTransferSetCanceled(long id) {
        // todo: test
        if (mQuery_updateTransferCanceled == null)
            mQuery_updateTransferCanceled = getWritableDatabase().compileStatement("UPDATE transfers SET canceled = 1, cancel_datetime = datetime('now', 'localtime') WHERE _id = ?");
        mQuery_updateTransferCanceled.bindLong(1, id);
        return mQuery_updateTransferCanceled.executeUpdateDelete();
    }

    private SQLiteStatement mQuery_updateTransfersSetCanceledIfNotFinalized = null;
    public synchronized long query_updateTransfersSetCanceledIfNotFinalized() {
        // todo: test
        if (mQuery_updateTransfersSetCanceledIfNotFinalized == null)
            mQuery_updateTransfersSetCanceledIfNotFinalized = getWritableDatabase().compileStatement("UPDATE transfers SET canceled = 1 WHERE finalized = 0");
        return mQuery_updateTransfersSetCanceledIfNotFinalized.executeUpdateDelete();
    }

    private SQLiteStatement mQuery_updateTransferSetCanceledWithBatchId = null;
    public synchronized long query_updateTransferSetCanceledWithBatchId(long batchId) {
        // todo: test
        if (mQuery_updateTransferSetCanceledWithBatchId == null)
            mQuery_updateTransferSetCanceledWithBatchId = getWritableDatabase().compileStatement("UPDATE transfers SET canceled = 1 WHERE batch_id = ?");
        mQuery_updateTransferSetCanceledWithBatchId.bindLong(1, batchId);
        return mQuery_updateTransferSetCanceledWithBatchId.executeUpdateDelete();
    }

    private SQLiteStatement mQuery_getBatchCount = null;
    public synchronized long query_getBatchCount() {
        // todo: test
        if (mQuery_getBatchCount == null)
            mQuery_getBatchCount = getReadableDatabase().compileStatement("SELECT COUNT(*) FROM batches");
        return mQuery_getBatchCount.simpleQueryForLong();
    }

    private SQLiteStatement mQuery_insertBatch = null;
    public synchronized long query_insertBatch() {
        // todo: test
        if (mQuery_insertBatch == null)
            mQuery_insertBatch = getWritableDatabase().compileStatement("INSERT INTO batches ( start_datetime ) VALUES ( datetime('now', 'localtime') )");
        return mQuery_insertBatch.executeInsert();
    }

    private SQLiteStatement mQuery_getLastBatchId = null;
    public long query_getLastBatchId() {
        // todo: test
        if (mQuery_getLastBatchId == null)
            mQuery_getLastBatchId = getReadableDatabase().compileStatement("SELECT _id FROM batches ORDER BY _id DESC LIMIT 1");
        return mQuery_getLastBatchId.simpleQueryForLong();
    }

    public Cursor query_getItem(long itemId) {
        return getReadableDatabase().rawQuery("SELECT * FROM items WHERE _id = ? ORDER BY _id", new String[]{String.valueOf(itemId)});
    }

    public Cursor query_getLastItemWithBarcode(String barcode) {
        return getReadableDatabase().rawQuery("SELECT * FROM items WHERE barcode = ? ORDER BY _id DESC LIMIT 1", new String[]{barcode});
    }

    public Cursor query_getItemsWithTransferId(long transferId) {
        return getReadableDatabase().rawQuery("SELECT * FROM items WHERE transfer_id = ? ORDER BY _id", new String[]{String.valueOf(transferId)});
    }

    public Cursor query_getTransfer(long transferId) {
        return getReadableDatabase().rawQuery("SELECT * FROM transfers WHERE _id = ?", new String[]{String.valueOf(transferId)});
    }

    public Cursor query_getLastTransfer() {
        return getReadableDatabase().rawQuery("SELECT * FROM transfers ORDER BY _id DESC LIMIT 1", null);
    }

    public Cursor query_getPreviousTransfer(long id) {
        return getReadableDatabase().rawQuery("SELECT * FROM transfers WHERE _id < ? ORDER BY _id DESC LIMIT 1", new String[]{String.valueOf(id)});
    }

    public Cursor query_getNextTransfer(long id) {
        return getReadableDatabase().rawQuery("SELECT * FROM transfers WHERE _id > ? ORDER BY _id ASC LIMIT 1", new String[]{String.valueOf(id)});
    }

    public Cursor query_getLastActiveTransfer() {
        return getReadableDatabase().rawQuery("SELECT * FROM transfers WHERE finalized = 0 AND canceled = 0 ORDER BY _id DESC LIMIT 1", null);
    }

    public Cursor query_getFinalTransfersWithBatchId(long batchId) {
        return getReadableDatabase().rawQuery("SELECT * FROM transfers WHERE finalized = 1 AND canceled = 0 AND batch_id = ?", new String[]{String.valueOf(batchId)});
    }

    public Cursor query_getLastTransferWithBatchId(long batchId) {
        return getReadableDatabase().rawQuery("SELECT * FROM transfers WHERE batch_id = ? ORDER BY _id DESC LIMIT 1", new String[]{String.valueOf(batchId)});
    }

    public Cursor query_getLastActiveTransferWithBatchId(long batchId) {
        return getReadableDatabase().rawQuery("SELECT * FROM transfers WHERE finalized = 0 AND canceled = 0 AND batch_id = ? ORDER BY _id DESC LIMIT 1", new String[]{String.valueOf(batchId)});
    }

    public Cursor query_getBatch(long id) {
        return getReadableDatabase().rawQuery("SELECT * FROM transfers WHERE _id = ? ORDER BY _id DESC LIMIT 1", new String[]{String.valueOf(id)});
    }

    public Cursor query_getLastBatch() {
        return getReadableDatabase().rawQuery("SELECT * FROM transfers WHERE finalized = 0 AND canceled = 0 ORDER BY _id DESC LIMIT 1", null);
    }

    public Cursor query_getLastActiveBatch() {
        return getReadableDatabase().rawQuery("SELECT * FROM transfers WHERE saved = 0 AND canceled = 0 ORDER BY _id DESC LIMIT 1", null);
    }

    public static class UpgradeException extends SQLiteException {
        public UpgradeException() { }

        public UpgradeException(String error) {
            super(error);
        }

        public UpgradeException(String error, Throwable cause) {
            super(error, cause);
        }
    }

    public static class Key {
        public static final String ID = "_id";
        public static final String TRANSFER_ID = "transfer_id";
        public static final String BATCH_ID= "batch_id";
        public static final String COMMENTS = "comments";
        public static final String SIGNED = "signed";
        public static final String FINALIZED = "finalized";
        public static final String SAVED = "saved";
        public static final String CANCELED = "canceled";
        public static final String BARCODE = "barcode";
        public static final String QUANTITY = "quantity";
        public static final String LOCATION_BARCODE = "location_barcode";
        public static final String SCAN_DATETIME = "scan_datetime";
        public static final String START_DATETIME = "start_datetime";
        public static final String SIGN_DATETIME = "sign_datetime";
        public static final String FINALIZE_DATETIME = "finalize_datetime";
        public static final String CANCEL_DATETIME = "cancel_datetime";
        public static final String SAVE_DATETIME = "save_datetime";
    }

    public static class Index {
        public static final String ITEMS_BARCODE_INDEX = "items_barcode_index";
        public static final String ITEMS_TRANSFER_ID_INDEX = "items_transfer_id_index";
        public static final String TRANSFERS_BATCH_ID_INDEX = "transfers_batch_id_index";
        public static final String TRANSFERS_LOCATION_BARCODE_INDEX = "transfers_location_barcode_index";
        public static final String TRANSFERS_FINALIZED_CANCELED_INDEX = "transfer_finalized_index";
    }

    public static class ItemTable {
        public static final String NAME = "items";
        public static final String SQL_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS items ( " +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "transfer_id INTEGER NOT NULL, " +
                "barcode TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL DEFAULT 1, " +
                "scan_datetime DATETIME NOT NULL )";
        public static final String SQL_CREATE_INDEX_TRANSFER_ID = "CREATE INDEX IF NOT EXISTS items_transfer_id_index ON items ( transfer_id )";
        public static final String SQL_CREATE_INDEX_BARCODE = "CREATE INDEX IF NOT EXISTS items_barcode_index ON items ( barcode )";
    }

    public static class TransferTable {
        public static final String NAME = "transfers";
        public static final String SQL_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS transfers ( " +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "batch_id INTEGER, " +
                "location_barcode TEXT NOT NULL, " +
                "signed INTEGER NOT NULL DEFAULT 0, " +
                "finalized INTEGER NOT NULL DEFAULT 0, " +
                "canceled INTEGER NOT NULL DEFAULT 0, " +
                "saved INTEGER DEFAULT 0, " +
                "start_datetime DATETIME NOT NULL, " +
                "sign_datetime DATETIME DEFAULT NULL, " +
                "finalize_datetime DATETIME DEFAULT NULL, " +
                "cancel_datetime DATETIME DEFAULT NULL, " +
                "save_datetime DATETIME DEFAULT NULL, " +
                "comments TEXT DEFAULT NULL )";
        public static final String SQL_CREATE_INDEX_FINALIZED_CANCELED = "CREATE INDEX IF NOT EXISTS transfer_finalized_index ON transfers ( finalized, canceled )";
        public static final String SQL_CREATE_INDEX_LOCATION_BARCODE = "CREATE INDEX IF NOT EXISTS transfers_location_barcode_index ON transfers ( location_barcode )";
        public static final String SQL_CREATE_INDEX_BATCH_ID = "CREATE INDEX IF NOT EXISTS transfers_batch_id_index ON transfers ( batch_id )";
    }

    public static class BatchTable {
        public static final String NAME = "batches";
        public static final String SQL_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS batches ( " +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "start_datetime DATETIME NOT NULL )";
    }
}
