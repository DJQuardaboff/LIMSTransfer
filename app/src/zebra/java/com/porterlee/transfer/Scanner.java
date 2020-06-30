package com.porterlee.transfer;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.barcode.BarcodeManager;
import com.symbol.emdk.barcode.ScanDataCollection;
import com.symbol.emdk.barcode.ScannerException;
import com.symbol.emdk.barcode.ScannerInfo;
import com.symbol.emdk.barcode.ScannerResults;
import com.symbol.emdk.barcode.StatusData;

import java.util.concurrent.atomic.AtomicBoolean;

public class Scanner implements EMDKManager.EMDKListener, com.symbol.emdk.barcode.Scanner.DataListener, BarcodeManager.ScannerConnectionListener, com.symbol.emdk.barcode.Scanner.StatusListener {
    private static final String TAG = Scanner.class.getCanonicalName();
    private static final String EMDK_PERMISSION = "com.symbol.emdk.permission.EMDK";

    private Scanner() { }
    private static Scanner mInstance;
    @NonNull
    public static Scanner getInstance() {
        return mInstance != null ? mInstance : (mInstance = new Scanner());
    }

    private boolean mIsEnabled = true;
    private volatile EMDKManager mEmdkManager = null;
    private volatile AtomicBoolean mEmdkManagerInAsyncInit = new AtomicBoolean(false);
    private final Object mLock = new Object();
    private BarcodeManager mBarcodeManager = null;
    private com.symbol.emdk.barcode.Scanner mScanner = null;

    public boolean getIsEnabled() {
        return mIsEnabled;
    }

    public void setIsEnabled(boolean isEnabled) {
        mIsEnabled = isEnabled;
        if (isEnabled) {
            enable();
        } else {
            disable();
        }
    }

    public void enable() {
        try {
            mScanner.enable();
            mScanner.triggerType = com.symbol.emdk.barcode.Scanner.TriggerType.HARD;
            if (!mScanner.isReadPending()) {
                mScanner.read();
            }
        } catch (ScannerException e) {
            throw new RuntimeException(e);
        }
    }

    public void disable() {
        try {
            mScanner.triggerType = com.symbol.emdk.barcode.Scanner.TriggerType.SOFT_ALWAYS;
            if (mScanner.isReadPending()) {
                mScanner.cancelRead();
            }
        } catch (ScannerException e) {
            throw new RuntimeException(e);
        }
    }

    public void init() {
        Context context = ScannerUtils.getInstance().getApplicationContext();

        if (context == null) {
            String message = "init() - context is null!";
            Log.e(TAG, message);
            throw new NullPointerException(message);
        }

        if (context.checkSelfPermission(EMDK_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "init() - permission '" + EMDK_PERMISSION + "' not granted!");
        }

        mEmdkManagerInAsyncInit.set(true);
        EMDKResults results = EMDKManager.getEMDKManager(context, this);

        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
            Log.e(TAG, "init() - EMDKManager object request failed!");
            return;
        }

        if (mEmdkManager != null) {
            mEmdkManagerInAsyncInit.set(false);
        }
    }

    public void start() {
        if (mEmdkManager == null) {
            if (!mEmdkManagerInAsyncInit.get()) {
                throw new NullPointerException("start() - mEmdkManager is null!");
            }
            return;
        }

        initBarcodeManager();
        initScanner();
        enable();
    }

    public void stop() {
        deInitScanner();
        deInitBarcodeManager();
    }

    public void release() {
        // Release all the resources
        if (mEmdkManager != null) {
            mEmdkManager.release();
            mEmdkManager = null;
        }
    }

    public boolean isReady() {
        return mEmdkManager != null && mBarcodeManager != null && mScanner != null;
    }

    private void initBarcodeManager(){
        if (mEmdkManager == null) {
            String message = "initBarcodeManager() - mEmdkManager is null!";
            Log.e(TAG, message);
            throw new NullPointerException(message);
        }

        mBarcodeManager = (BarcodeManager) mEmdkManager.getInstance(EMDKManager.FEATURE_TYPE.BARCODE);

        if (mBarcodeManager == null) {
            String message = "initBarcodeManager() - mBarcodeManager is null!";
            Log.e(TAG, message);
            throw new NullPointerException(message);
        }

        mBarcodeManager.addConnectionListener(this);
    }

    private void deInitBarcodeManager(){
        if (mBarcodeManager == null) {
            String message = "deInitBarcodeManager() - mBarcodeManager is null!";
            Log.e(TAG, message);
            throw new NullPointerException(message);
        }

        mBarcodeManager.removeConnectionListener(this);

        if (mEmdkManager == null) {
            String message = "deInitBarcodeManager() - mEmdkManager is null!";
            Log.e(TAG, message);
            throw new NullPointerException(message);
        }

        mEmdkManager.release(EMDKManager.FEATURE_TYPE.BARCODE);

        mBarcodeManager = null;
    }

    private void initScanner() {
        if (mScanner != null) {
            String message = "initScanner() - mScanner is not null!";
            Log.w(TAG, message);
            deInitScanner();
        }

        if (mBarcodeManager == null) {
            String message = "initScanner() - mScanner is not null!";
            Log.e(TAG, message);
            throw new RuntimeException(message);
        }

        mScanner = mBarcodeManager.getDevice(BarcodeManager.DeviceIdentifier.DEFAULT);

        if (mScanner == null) {
            String message = "initScanner() - failed to initialize mScanner!";
            Log.e(TAG, message);
            throw new RuntimeException(message);
        }

        mScanner.addDataListener(this);
        mScanner.addStatusListener(this);

        try {
            mScanner.enable();
        } catch (ScannerException e) {
            e.printStackTrace();
            deInitScanner();
        }

        mScanner.triggerType = com.symbol.emdk.barcode.Scanner.TriggerType.HARD;
    }

    private void deInitScanner() {
        if (mScanner == null) {
            String message = "deInitScanner() - mScanner is null!";
            Log.v(TAG, message);
            return;
        }

        try {
            mScanner.disable();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            mScanner.removeDataListener(this);
            mScanner.removeStatusListener(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            mScanner.release();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        mScanner = null;
    }

    @Override
    public void onOpened(EMDKManager emdkManager) {
        Log.v(TAG, "EMDK open success!");
        mEmdkManager = emdkManager;
        mEmdkManagerInAsyncInit.set(false);
        start();
    }

    @Override
    public void onClosed() {
        if (mEmdkManager != null) {
            mEmdkManager.release();
            mEmdkManager = null;
        }
        Log.e(TAG, "EMDK closed unexpectedly! Please close and restart the application.");
    }

    @Override
    public void onData(ScanDataCollection scanDataCollection) {
        if (ScannerResults.SUCCESS.equals(scanDataCollection.getResult())) {
            String barcode = scanDataCollection.getScanData().get(0).getData();
            ScannerUtils.getInstance().onBarcodeScanned(barcode == null ? "" : barcode);
        } else {
            ScannerUtils.getInstance().onScanComplete(false);
        }
    }

    @Override
    public void onStatus(StatusData statusData) {
        StatusData.ScannerStates state = statusData.getState();
        switch (state) {
            case IDLE:
                if (mIsEnabled) {
                    try {
                        if (!mScanner.isReadPending()) {
                            mScanner.read();
                        }
                    } catch (ScannerException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case WAITING:
                if (!mIsEnabled) {
                    try {
                        if (mScanner.isReadPending()) {
                            mScanner.cancelRead();
                        }
                    } catch (ScannerException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case SCANNING:
                break;
            case DISABLED:
                Toast.makeText(ScannerUtils.getInstance().getApplicationContext(), statusData.getFriendlyName() + " is disabled.", Toast.LENGTH_SHORT).show();
                break;
            case ERROR:
                Toast.makeText(ScannerUtils.getInstance().getApplicationContext(), "An error has occurred.", Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
    }

    @Override
    public void onConnectionChange(ScannerInfo scannerInfo, BarcodeManager.ConnectionState connectionState) {
        if (scannerInfo.getDeviceIdentifier() == BarcodeManager.DeviceIdentifier.DEFAULT) {
            switch (connectionState) {
                case CONNECTED:
                    //bSoftTriggerSelected = false;
                    synchronized (mLock) {
                        initScanner();
                        //bExtScannerDisconnected = false;
                    }
                    break;
                case DISCONNECTED:
                    //bExtScannerDisconnected = true;
                    synchronized (mLock) {
                        deInitScanner();
                    }
                    break;
            }
        }

        Log.e(TAG, "Scanner '" + scannerInfo.getFriendlyName() + "' was " + connectionState.name().toLowerCase());
    }
}
