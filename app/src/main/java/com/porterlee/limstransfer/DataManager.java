package com.porterlee.limstransfer;

import android.content.Context;
import android.graphics.Bitmap;

import org.jetbrains.annotations.NotNull;

public class DataManager {
    private volatile TransferDatabase mTransferDatabase;
    private Location mCurrentLocation;
    private Bitmap mSignature;
    private String mPreviousPrefix;
    private String mPreviousPostfix;

    public DataManager(Context context) {
        mTransferDatabase = new TransferDatabase(context);
    }

    public TransferDatabase getDatabase() {
        return mTransferDatabase;
    }

    public Location getCurrentLocation() {
        return mCurrentLocation;
    }

    public void setCurrentLocation(@NotNull Location location) {
        this.mCurrentLocation = location;
    }

    public long getTransferId() {
        return mTransferDatabase.select_seq_from_sqlite_sequence_where_name_equals(TransferDatabase.LocationTable.NAME);
    }

    public Bitmap getSignature() {
        return mSignature;
    }

    public void setSignature(Bitmap mSignature) {
        this.mSignature = mSignature;
    }

    public String getPreviousPrefix() {
        return mPreviousPrefix;
    }

    public void setPreviousPrefix(String previousPrefix) {
        this.mPreviousPrefix = previousPrefix;
    }

    public String getPreviousPostfix() {
        return mPreviousPostfix;
    }

    public void setPreviousPostfix(String previousPostfix) {
        this.mPreviousPostfix = previousPostfix;
    }

    /**
     * Inserts a barcode into the {@link TransferDatabase} and returns a {@link Location}
     *
     * @param barcode the barcode of the location to be added to the database
     * @return a {@link Location} object representing a row in the locations table
     */
    public Location newLocation(String barcode) {
        return new Location(mTransferDatabase.insert_barcode_into_locationTable(barcode), barcode);
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

        public String[] getPrefixes() {
            return prefixes;
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

    public class Location {
        public long id;
        public String barcode;

        private Location(long id, String barcode) {
            this.id = id;
            this.barcode = barcode;
        }
    }

    public class Item {
        public long id;
        public long locationId;
        public String barcode;

        private Item(long id, long locationId, String barcode) {
            this.id = id;
            this.locationId = locationId;
            this.barcode = barcode;
        }
    }
}
