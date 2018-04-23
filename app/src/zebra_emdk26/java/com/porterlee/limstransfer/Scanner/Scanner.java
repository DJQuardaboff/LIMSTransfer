package com.porterlee.limstransfer.Scanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.porterlee.limstransfer.DebugLog;
import com.symbol.emdk.EMDKException;
import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.ProfileManager;

public class Scanner extends AbstractScanner implements EMDKManager.EMDKListener {
    public static final String TAG = Scanner.class.getSimpleName();
    private static final String PROFILE_NAME = "PLCTransfer";
    private static final String EXTRA_SOURCE = "com.symbol.datawedge.source";
    private static final String SOURCE_SCANNER = "scanner";
    private static final String EXTRA_DATA_STRING = "com.symbol.datawedge.data_string";
    private final IntentFilter resultFilter = new IntentFilter("com.symbol.emdk.BARCODE_SCAN");
    private final BroadcastReceiver mResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getStringExtra(EXTRA_SOURCE).equalsIgnoreCase(SOURCE_SCANNER)){
                String data = intent.getStringExtra(EXTRA_DATA_STRING);
                if (data != null && data.length() > 0) {
                    onBarcodeScanned(data);
                }
            }
        }
    };

    @Override
    public boolean init(Context context) {
        return EMDKManager.getEMDKManager(context, this).statusCode == EMDKResults.STATUS_CODE.SUCCESS;
    }

    @Override
    protected boolean onScanModeChanged(int scanMode) {
        switch (scanMode) {
            case ONE_SHOT_MODE:
                return true;
            case CONTINUOUS_MODE: default:
                return false;
        }
    }

    @Override
    public void onOpened(EMDKManager emdkManager) {
        try {
            emdkManager.getInstanceAsync(EMDKManager.FEATURE_TYPE.PROFILE, (statusData, emdkBase) -> {
                ProfileManager mProfileManager = (ProfileManager) emdkBase;
                if (mProfileManager != null) {
                    mProfileManager.addDataListener(resultData -> {
                        if (resultData.getResult().statusCode.equals(EMDKResults.STATUS_CODE.FAILURE)) {
                            DebugLog.d(TAG, "Profile set failure");
                            DebugLog.d(TAG, "compatibilityCheck=" + mProfileManager.processProfile(PROFILE_NAME, ProfileManager.PROFILE_FLAG.CHECK_COMPATIBILITY, new String[] { "" }).statusCode.name());
                        } else {
                            DebugLog.d(TAG, "profileSetStatusCode=" + resultData.getResult().statusCode.name());
                        }
                        emdkManager.release();
                    });
                    mProfileManager.processProfileAsync(PROFILE_NAME, ProfileManager.PROFILE_FLAG.SET, new String[] { "" });
                } else {
                    Log.w(TAG, "Profile error");
                }
            });
        } catch (EMDKException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClosed() {

    }

    @Override
    public void onResume(Context context) {
        context.registerReceiver(mResultReceiver, resultFilter);
    }

    @Override
    public void onPause(Context context) {
        try {
            context.unregisterReceiver(mResultReceiver);
        } catch (IllegalArgumentException ignored) { }
    }

    @Override
    public void onDestroy(Context context) {
        try {
            context.unregisterReceiver(mResultReceiver);
        } catch (IllegalArgumentException ignored) { }
    }
}
