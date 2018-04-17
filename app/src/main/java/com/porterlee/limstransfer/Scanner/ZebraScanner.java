package com.porterlee.limstransfer.Scanner;

import android.os.Build;

import java.util.Collections;
import java.util.List;

public class ZebraScanner extends Scanner {
    private static final List<String> mCompatibleManufacturers = Collections.singletonList("Zebra Technologies");
    private static final List<String> mCompatibleModels = Collections.singletonList("TC20");

    @Override
    public boolean init() {
        return false;
    }

    @Override
    protected boolean onScanModeChanged(int scanMode) {
        switch (scanMode) {
            case CONTINUOUS_MODE:

                return true;
            case ONE_SHOT_MODE:

                return true;
            default:
                return false;
        }
    }

    public static boolean isCompatible() {
        return mCompatibleManufacturers.contains(Build.MANUFACTURER) && mCompatibleModels.contains(Build.MODEL);
    }
}
