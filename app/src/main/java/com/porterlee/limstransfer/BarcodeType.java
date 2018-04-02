package com.porterlee.limstransfer;

public enum BarcodeType {
    Item("e1", "E", "t", "T"),
    Container("m1", "M", "A", "a"),
    Location("V", "L5"),
    Process("L3"),
    Invalid("");

    private String[] prefixes;

    BarcodeType(String... prefixes) {
        this.prefixes = prefixes;
    }

    public String[] getPrefixes() {
        return prefixes;
    }

    public static BarcodeType getBarcodeType(String barcode) {
        /*if (barcode.startsWith("e1") || barcode.startsWith("E") || barcode.startsWith("t") || barcode.startsWith("T"))
            return Item;
        if (barcode.startsWith("m1") || barcode.startsWith("M") || barcode.startsWith("A") || barcode.startsWith("a"))
            return Container;
        if (barcode.startsWith("V") || barcode.startsWith("L5"))
            return Location;
        if (barcode.startsWith("L3"))
            return Process;*/
        for(BarcodeType barcodeType : BarcodeType.values())
            for (String prefix : barcodeType.getPrefixes())
                if (barcode.startsWith(prefix))
                    return barcodeType;
        return Invalid;
    }
}
