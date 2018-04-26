package com.porterlee.limstransfer;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import org.jetbrains.annotations.NotNull;

public final class TransferDatabase {
    public static final String NAME = "lims_transfer";
    private SQLiteDatabase mDatabase;
    private SQLiteStatement mSelect_count_from_itemTable_where_transferId_equals;
    private SQLiteStatement mSelect_count_from_itemTable_where_transferId_equals_and_barcode_equals;
    private SQLiteStatement mInsert_transferId_barcode_into_itemTable;
    private SQLiteStatement mDelete_from_itemTable_where_id_equals;
    private SQLiteStatement mDelete_from_itemTable_where_transferId_equals;
    private SQLiteStatement mInsert_locationBarcode_into_transferTable;
    private SQLiteStatement mUpdate_transferTable_set_signed_equalTo_where_id_equals;
    private SQLiteStatement mUpdate_transferTable_set_finalized_equalTo_where_id_equals;
    private SQLiteStatement mUpdate_transferTable_set_canceled_equalTo_where_id_equals;
    private SQLiteStatement mDelete_from_transferTable_where_id_equals;

    public TransferDatabase(Context context) {
        reinit(context);
    }

    public void reinit(Context context) {
        mDatabase = context.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null);

        ItemTable.init(mDatabase);
        TransferTable.init(mDatabase);

        mSelect_count_from_itemTable_where_transferId_equals = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + Key.TRANSFER_ID + " = ?");
        mSelect_count_from_itemTable_where_transferId_equals_and_barcode_equals = mDatabase.compileStatement("SELECT COUNT(*) FROM " + ItemTable.NAME + " WHERE " + Key.TRANSFER_ID + " = ? AND " + Key.BARCODE + " = ?");
        mInsert_transferId_barcode_into_itemTable = mDatabase.compileStatement("INSERT INTO " + ItemTable.NAME + " ( " + Key.TRANSFER_ID + ", " + Key.BARCODE + ", " + Key.DATE_TIME + " ) VALUES ( ?, ?, datetime('now', 'localtime') )");
        mDelete_from_itemTable_where_id_equals = mDatabase.compileStatement("DELETE FROM " + ItemTable.NAME + " WHERE " + Key.ID + " = ?");
        mDelete_from_itemTable_where_transferId_equals = mDatabase.compileStatement("DELETE FROM " + ItemTable.NAME + " WHERE " + Key.TRANSFER_ID + " = ?");

        mInsert_locationBarcode_into_transferTable = mDatabase.compileStatement("INSERT INTO " + TransferTable.NAME + " ( " + Key.SIGNED + ", " + Key.FINALIZED + ", " + Key.CANCELED + ", " + Key.LOCATION_BARCODE + ", " + Key.START_DATE_TIME + " ) VALUES ( '0', '0', '0', ?, datetime('now', 'localtime') )");
        mUpdate_transferTable_set_signed_equalTo_where_id_equals = mDatabase.compileStatement("UPDATE " + TransferTable.NAME + " SET " + Key.SIGNED + " = ?, " + Key.SIGN_DATE_TIME + " = datetime('now', 'localtime') WHERE " + Key.ID + " = ?");
        mUpdate_transferTable_set_finalized_equalTo_where_id_equals = mDatabase.compileStatement("UPDATE " + TransferTable.NAME + " SET " + Key.FINALIZED + " = ?, " + Key.FINALIZE_DATE_TIME + " = datetime('now', 'localtime') WHERE " + Key.ID + " = ?");
        mUpdate_transferTable_set_canceled_equalTo_where_id_equals = mDatabase.compileStatement("UPDATE " + TransferTable.NAME + " SET " + Key.CANCELED + " = ? WHERE " + Key.ID + " = ?");
        mDelete_from_transferTable_where_id_equals = mDatabase.compileStatement("DELETE FROM " + TransferTable.NAME + " WHERE " + Key.ID + " = ?");
    }

    public SQLiteDatabase getDatabase() {
        return mDatabase;
    }

    public boolean delete(@NotNull Context context) {
        return context.deleteDatabase(NAME);
    }

    public boolean isOpen() {
        return mDatabase != null && mDatabase.isOpen();
    }

    public boolean exists(@NotNull Context context) {
        for (String name : context.databaseList())
            if (NAME.equals(name))
                return true;

        return false;
    }

    public void close() {
        mDatabase.close();
    }

    public synchronized long select_count_from_itemTable_where_transferId_equals(long transferId) {
        mSelect_count_from_itemTable_where_transferId_equals.bindLong(1, transferId);
        return mSelect_count_from_itemTable_where_transferId_equals.simpleQueryForLong();
    }

    public synchronized long select_count_from_itemTable_where_transferId_equals_and_barcode_equals(long transferId, String barcode) {
        mSelect_count_from_itemTable_where_transferId_equals_and_barcode_equals.bindLong(1, transferId);
        mSelect_count_from_itemTable_where_transferId_equals_and_barcode_equals.bindString(2, barcode);
        return mSelect_count_from_itemTable_where_transferId_equals_and_barcode_equals.simpleQueryForLong();
    }

    public synchronized long insert_transferId_barcode_into_itemTable(long transferId, String barcode) {
        mInsert_transferId_barcode_into_itemTable.bindLong(1, transferId);
        mInsert_transferId_barcode_into_itemTable.bindString(2, barcode);
        return mInsert_transferId_barcode_into_itemTable.executeInsert();
    }

    public synchronized long delete_from_itemTable_where_id_equals(long id) {
        mDelete_from_itemTable_where_id_equals.bindLong(1, id);
        return mDelete_from_itemTable_where_id_equals.executeUpdateDelete();
    }

    public synchronized long delete_from_itemTable_where_transferId_equals(long transferId) {
        mDelete_from_itemTable_where_transferId_equals.bindLong(1, transferId);
        return mDelete_from_itemTable_where_transferId_equals.executeUpdateDelete();
    }

    public synchronized long insert_locationBarcode_into_transferTable(String barcode) {
        mInsert_locationBarcode_into_transferTable.bindString(1, barcode);
        return mInsert_locationBarcode_into_transferTable.executeInsert();
    }

    public synchronized long update_transferTable_set_signed_equalTo_where_id_equals(boolean signed, long id) {
        mUpdate_transferTable_set_signed_equalTo_where_id_equals.bindLong(1, signed ? 1 : 0);
        mUpdate_transferTable_set_signed_equalTo_where_id_equals.bindLong(2, id);
        return mUpdate_transferTable_set_signed_equalTo_where_id_equals.executeUpdateDelete();
    }

    public synchronized long update_transferTable_set_finalized_equalTo_where_id_equals(boolean finalized, long id) {
        mUpdate_transferTable_set_finalized_equalTo_where_id_equals.bindLong(1, finalized ? 1 : 0);
        mUpdate_transferTable_set_finalized_equalTo_where_id_equals.bindLong(2, id);
        return mUpdate_transferTable_set_finalized_equalTo_where_id_equals.executeUpdateDelete();
    }

    public synchronized long update_transferTable_set_canceled_equalTo_where_id_equals(boolean canceled, long id) {
        mUpdate_transferTable_set_canceled_equalTo_where_id_equals.bindLong(1, canceled ? 1 : 0);
        mUpdate_transferTable_set_canceled_equalTo_where_id_equals.bindLong(2, id);
        return mUpdate_transferTable_set_canceled_equalTo_where_id_equals.executeUpdateDelete();
    }

    public synchronized long delete_from_transferTable_where_id_equals(long id) {
        mDelete_from_transferTable_where_id_equals.bindLong(1, id);
        return mDelete_from_transferTable_where_id_equals.executeUpdateDelete();
    }

    public static class Key {
        public static final String ID = "_id";
        public static final String TRANSFER_ID = "transfer_id";
        public static final String SIGNED = "signed";
        public static final String FINALIZED = "finalized";
        public static final String CANCELED = "canceled";
        public static final String BARCODE = "barcode";
        public static final String LOCATION_BARCODE = "location_barcode";
        public static final String DATE_TIME = "date_time";
        public static final String START_DATE_TIME = "start_date_time";
        public static final String SIGN_DATE_TIME = "sign_date_time";
        public static final String FINALIZE_DATE_TIME = "finalize_date_time";
    }

    public static class Index {
        public static final String ITEMS_BARCODE_INDEX = "items_barcode_index";
        public static final String ITEMS_TRANSFER_ID_INDEX = "items_transfer_id_index";
        public static final String TRANSFERS_LOCATION_BARCODE_INDEX = "transfers_location_barcode_index";
        public static final String TRANSFERS_FINALIZED_CANCELED_INDEX = "transfer_finalized_index";
    }

    public static class ItemTable {
        public static final String NAME = "items";

        private static void init(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + NAME + " ( " + TransferDatabase.Key.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + TransferDatabase.Key.TRANSFER_ID + " INTEGER NOT NULL, " + TransferDatabase.Key.BARCODE + " TEXT NOT NULL, " + TransferDatabase.Key.DATE_TIME + " DATETIME NOT NULL )");
            database.execSQL("CREATE INDEX IF NOT EXISTS " + Index.ITEMS_TRANSFER_ID_INDEX + " ON " + NAME + " ( " + TransferDatabase.Key.TRANSFER_ID + " )");
            database.execSQL("CREATE INDEX IF NOT EXISTS " + Index.ITEMS_BARCODE_INDEX + " ON " + NAME + " ( " + TransferDatabase.Key.BARCODE + " )");
        }

        public static class Key {
            public static final String ID = NAME + "." + TransferDatabase.Key.ID;
            public static final String TRANSFER_ID = NAME + "." + TransferDatabase.Key.TRANSFER_ID;
            public static final String BARCODE = NAME + "." + TransferDatabase.Key.BARCODE;
            public static final String DATE_TIME = NAME + "." + TransferDatabase.Key.DATE_TIME;
        }
    }

    public static class TransferTable {
        public static final String NAME = "transfers";

        private static void init(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + NAME + " ( " + TransferDatabase.Key.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + TransferDatabase.Key.SIGNED + " INTEGER NOT NULL, " + TransferDatabase.Key.FINALIZED + " INTEGER NOT NULL, " + TransferDatabase.Key.CANCELED + " INTEGER NOT NULL, " + TransferDatabase.Key.LOCATION_BARCODE + " TEXT NOT NULL, " + TransferDatabase.Key.START_DATE_TIME + " DATETIME NOT NULL, " + TransferDatabase.Key.SIGN_DATE_TIME + " DATETIME, " + TransferDatabase.Key.FINALIZE_DATE_TIME + " DATETIME )");
            database.execSQL("CREATE INDEX IF NOT EXISTS " + Index.TRANSFERS_FINALIZED_CANCELED_INDEX + " ON " + NAME + " ( " + TransferDatabase.Key.FINALIZED + ", " + TransferDatabase.Key.CANCELED + " )");
            database.execSQL("CREATE INDEX IF NOT EXISTS " + Index.TRANSFERS_LOCATION_BARCODE_INDEX + " ON " + NAME + " ( " + TransferDatabase.Key.LOCATION_BARCODE + " )");
        }

        public static class Key {
            public static final String ID = NAME + "." + TransferDatabase.Key.ID;
            public static final String SIGNED = NAME + "." + TransferDatabase.Key.SIGNED;
            public static final String FINALIZED = NAME + "." + TransferDatabase.Key.FINALIZED;
            public static final String CANCELED = NAME + "." + TransferDatabase.Key.CANCELED;
            public static final String LOCATION_BARCODE = NAME + "." + TransferDatabase.Key.LOCATION_BARCODE;
            public static final String START_DATE_TIME = NAME + "." + TransferDatabase.Key.START_DATE_TIME;
            public static final String SIGN_DATE_TIME = NAME + "." + TransferDatabase.Key.SIGN_DATE_TIME;
            public static final String FINALIZE_DATE_TIME = NAME + "." + TransferDatabase.Key.FINALIZE_DATE_TIME;
        }
    }
}
