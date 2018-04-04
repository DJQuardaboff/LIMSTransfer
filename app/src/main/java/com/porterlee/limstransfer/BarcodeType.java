package com.porterlee.limstransfer;

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

    public static BarcodeType getBarcodeType(String barcode) {
        if (barcode == null)
            return Invalid;
        for(BarcodeType barcodeType : BarcodeType.values())
            for (String prefix : barcodeType.getPrefixes())
                if (barcode.startsWith(prefix))
                    return barcodeType;
        return Invalid;
    }
}
