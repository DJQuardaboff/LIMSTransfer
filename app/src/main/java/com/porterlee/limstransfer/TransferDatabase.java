package com.porterlee.limstransfer;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import org.jetbrains.annotations.NotNull;

public final class TransferDatabase {
    public static final String NAME = "lims_transfer";
    private SQLiteDatabase mDatabase;
    private SQLiteStatement mSelect_barcode_from_itemTable_where_id_equals;
    private SQLiteStatement mInsert_locationId_barcode_into_itemTable;
    private SQLiteStatement mDelete_from_itemTable_where_id_equals;
    private SQLiteStatement mSelect_barcode_from_locationTable_where_id_equals;
    private SQLiteStatement mInsert_barcode_into_locationTable;
    private SQLiteStatement mDelete_from_locationTable_where_id_equals;

    public TransferDatabase(Context context) {
        reinit(context);
    }

    public void reinit(Context context) {
        mDatabase = context.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null);
        ItemTable.init(mDatabase);
        LocationTable.init(mDatabase);
        mSelect_barcode_from_itemTable_where_id_equals = mDatabase.compileStatement("SELECT " + Key.BARCODE + " FROM " + ItemTable.NAME + " WHERE " + Key.ID + " = ?");
        mInsert_locationId_barcode_into_itemTable = mDatabase.compileStatement("INSERT INTO " + ItemTable.NAME + " ( " + Key.LOCATION_ID + ", " + Key.BARCODE + " ) VALUES ( ?, ? )");
        mDelete_from_itemTable_where_id_equals = mDatabase.compileStatement("DELETE FROM " + ItemTable.NAME + " WHERE " + Key.ID + " = ?");
        mSelect_barcode_from_locationTable_where_id_equals = mDatabase.compileStatement("SELECT " + Key.BARCODE + " FROM " + LocationTable.NAME + " WHERE " + Key.ID + " = ?");
        mInsert_barcode_into_locationTable = mDatabase.compileStatement("INSERT INTO " + LocationTable.NAME + " ( " + Key.BARCODE + " ) VALUES ( ? )");
        mDelete_from_locationTable_where_id_equals = mDatabase.compileStatement("DELETE FROM " + LocationTable.NAME + " WHERE " + Key.ID + " = ?");
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

    public synchronized String select_barcode_from_itemTable_where_id_equals(long id) {
        mSelect_barcode_from_itemTable_where_id_equals.bindLong(1, id);
        return mSelect_barcode_from_itemTable_where_id_equals.simpleQueryForString();
    }

    public synchronized long insert_locationId_barcode_into_itemTable(long locationId, String barcode) {
        mInsert_locationId_barcode_into_itemTable.bindLong(1, locationId);
        mInsert_locationId_barcode_into_itemTable.bindString(2, barcode);
        return mInsert_locationId_barcode_into_itemTable.executeInsert();
    }

    public synchronized long delete_from_itemTable_where_id_equals(long id) {
        mDelete_from_itemTable_where_id_equals.bindLong(1, id);
        return mDelete_from_itemTable_where_id_equals.executeUpdateDelete();
    }

    public Cursor getItemListCursor() {
        return mDatabase.query(ItemTable.NAME, new String[] { Key.ID, Key.LOCATION_ID, Key.BARCODE }, null, null, null, null, Key.ID + " DESC");
    }

    public synchronized String select_barcode_from_locationTable_where_id_equals(long id) {
        mSelect_barcode_from_locationTable_where_id_equals.bindLong(1, id);
        return mSelect_barcode_from_locationTable_where_id_equals.simpleQueryForString();
    }

    public synchronized long insert_barcode_into_locationTable(String barcode) {
        mInsert_barcode_into_locationTable.bindString(1, barcode);
        return mInsert_barcode_into_locationTable.executeInsert();
    }

    public synchronized long delete_from_locationTable_where_id_equals(long id) {
        mDelete_from_locationTable_where_id_equals.bindLong(1, id);
        return mDelete_from_locationTable_where_id_equals.executeUpdateDelete();
    }

    public Cursor getLocationListCursor() {
        return mDatabase.query(LocationTable.NAME, new String[] { "MAX(" + Key.ID + ") AS _id", "MIN(" + Key.ID + ") AS min_id", "GROUP_CONCAT(" + Key.ID + ") AS all_ids", Key.BARCODE }, null, null, Key.BARCODE, null, "min_id DESC");
    }

    public void close() {
        mDatabase.close();
    }

    public static class Key {
        public static final String ID = "_id";
        public static final String LOCATION_ID = "location_id";
        public static final String BARCODE = "barcode";
        public static final String DATE_TIME = "date_time";
    }

    public static class Index {
        public static final String ITEM_BARCODE_INDEX = "item_barcode_index";
        public static final String LOCATION_BARCODE_INDEX = "location_barcode_index";
    }

    public static class ItemTable {
        public static final String NAME = "items";

        private static void init(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + NAME + " ( " + TransferDatabase.Key.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + TransferDatabase.Key.LOCATION_ID + " INTEGER NOT NULL, " + TransferDatabase.Key.BARCODE + " TEXT NOT NULL, " + TransferDatabase.Key.DATE_TIME + " DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL )");
            database.execSQL("CREATE INDEX IF NOT EXISTS " + Index.ITEM_BARCODE_INDEX + " ON " + NAME + " ( " + TransferDatabase.Key.BARCODE + " )");
        }

        public static class Key {
            public static final String ID = NAME + "." + TransferDatabase.Key.ID;
            public static final String LOCATION_ID = NAME + "." + TransferDatabase.Key.LOCATION_ID;
            public static final String BARCODE = NAME + "." + TransferDatabase.Key.BARCODE;
            public static final String DATE_TIME = NAME + "." + TransferDatabase.Key.DATE_TIME;
        }
    }

    public static class LocationTable {
        public static final String NAME = "locations";

        private static void init(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + NAME + " ( " + TransferDatabase.Key.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + TransferDatabase.Key.BARCODE + " TEXT NOT NULL, " + TransferDatabase.Key.DATE_TIME + " DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL )");
            database.execSQL("CREATE INDEX IF NOT EXISTS " + Index.LOCATION_BARCODE_INDEX + " ON " + NAME + " ( " + TransferDatabase.Key.BARCODE + " )");
        }

        public static class Key {
            public static final String ID = NAME + "." + TransferDatabase.Key.ID;
            public static final String BARCODE = NAME + "." + TransferDatabase.Key.BARCODE;
            public static final String DATE_TIME =NAME + "." + TransferDatabase.Key.DATE_TIME;
        }
    }
}
