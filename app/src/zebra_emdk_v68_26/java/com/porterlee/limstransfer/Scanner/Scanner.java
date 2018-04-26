package com.porterlee.limstransfer.Scanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.util.Log;

import com.porterlee.limstransfer.BuildConfig;
import com.porterlee.limstransfer.TransferActivity;
import com.symbol.emdk.EMDKException;
import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.ProfileManager;

public class Scanner extends AbstractScanner implements EMDKManager.EMDKListener {
    public static final String TAG = Scanner.class.getCanonicalName();
    private static final String PROFILE_NAME = "PLCTransfer";
    private static final String EXTRA_SOURCE = "com.symbol.datawedge.source";
    private static final String SOURCE_SCANNER = "scanner";
    private static final String EXTRA_DATA_STRING = "com.symbol.datawedge.data_string";
    private boolean isReady = false;
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
        try {
            return EMDKManager.getEMDKManager(context.getApplicationContext(), this).statusCode == EMDKResults.STATUS_CODE.SUCCESS;
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean isReady() {
        return isReady;
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
                Log.e(TAG, "test2");
                if (mProfileManager != null) {
                    final long temp = System.currentTimeMillis();
                    EMDKResults emdkResults = mProfileManager.processProfile(PROFILE_NAME, ProfileManager.PROFILE_FLAG.SET, new String[] { "" });
                    Log.e(TAG, "Time to process: " + (System.currentTimeMillis() - temp) + "ms");
                    if (emdkResults.statusCode.equals(EMDKResults.STATUS_CODE.FAILURE)) {
                        Log.w(TAG, "Profile set failure");
                        Log.v(TAG, "compatibilityCheck=" + mProfileManager.processProfile(PROFILE_NAME, ProfileManager.PROFILE_FLAG.CHECK_COMPATIBILITY, new String[] { "" }).statusCode.name());
                    } else {
                        Log.v(TAG, "profileSetStatusCode=" + emdkResults.statusCode.name());
                    }
                    emdkManager.release();
                    isReady = true;
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
        isReady = false;
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
