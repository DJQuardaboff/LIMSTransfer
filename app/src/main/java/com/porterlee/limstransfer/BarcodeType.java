package com.porterlee.limstransfer;

public enum BarcodeType {
    Item(BuildConfig.is_LAM_system ? new String[] { "j1", "J" } : new String[] { "e1", "E" }),
    Container("m1", "M"),
    Location("V", "L5"),
    Invalid();

    private final String[] prefixes;

    BarcodeType(String... prefixes) {
        this.prefixes = prefixes;
    }

    public boolean isOfType(String barcode) {
        if (this.equals(Invalid)) {
            return this.equals(getBarcodeType(barcode));
        }
        for (String prefix : prefixes) {
            if (barcode.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static BarcodeType getBarcodeType(String barcode) {
        if (barcode == null)
            return Invalid;
        for(int i = 0; i < BarcodeType.values().length - 1; i++)
            if (BarcodeType.values()[i].isOfType(barcode))
                return BarcodeType.values()[i];
        return Invalid;
    }
}
