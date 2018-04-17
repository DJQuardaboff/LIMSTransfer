package com.porterlee.limstransfer.Scanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.KeyEvent;

import com.porterlee.limstransfer.Manifest;

import java.util.Collections;
import java.util.List;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScanConst;
import device.scanner.ScannerService;

public class JanamScanner extends Scanner {
    private static final List<String> mCompatibleManufacturers = Collections.singletonList("Janam Technologies");
    private static final List<String> mCompatibleModels = Collections.singletonList("XM5");
    private IScannerService mScanner;
    private String mPreviousPrefix;
    private String mPreviousPostfix;
    private final DecodeResult mDecodeResult = new DecodeResult();
    private final BroadcastReceiver mResultReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                mScanner.aDecodeGetResult(mDecodeResult);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            if (mScanner != null) {
                if (!"READ FAIL".equals(mDecodeResult.symName))
                    onBarcodeScanned(mDecodeResult.decodeValue);
            }
        }
    };

    private final IntentFilter resultFilter = new IntentFilter();
    {
        resultFilter.setPriority(0);
        resultFilter.addAction("device.scanner.USERMSG");
    }

    private final BroadcastReceiver mScanKeyEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ScanConst.INTENT_SCANKEY_EVENT.equals(intent.getAction())) {
                final KeyEvent event = intent.getParcelableExtra(ScanConst.EXTRA_SCANKEY_EVENT);
                switch (event.getKeyCode()) {
                    case ScanConst.KEYCODE_SCAN_FRONT: case ScanConst.KEYCODE_SCAN_LEFT: case ScanConst.KEYCODE_SCAN_RIGHT: case ScanConst.KEYCODE_SCAN_REAR:
                        onScanKeyEvent(mScanner, event.getAction());
                }
            }
        }
    };

    private static void onScanKeyEvent(IScannerService scanner, int action) {
        if (scanner != null) {
            try {
                if (action == KeyEvent.ACTION_DOWN) {
                    scanner.aDecodeSetTriggerOn(1);
                } else if (action == KeyEvent.ACTION_UP) {
                    scanner.aDecodeSetTriggerOn(0);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean init() {
        if ((mScanner = IScannerService.Stub.asInterface(ServiceManager.getService("ScannerService"))) != null) {
            try {
                mScanner.aDecodeAPIInit();
                mScanner.aDecodeSetDecodeEnable(1);
                mScanner.aDecodeSetResultType(ScannerService.ResultType.DCD_RESULT_USERMSG);
                return true;
            } catch (RemoteException ignored) { }
        }
        return false;
    }

    @Override
    protected boolean onScanModeChanged(int scanMode) {
        try {
            switch (scanMode) {
                case CONTINUOUS_MODE:
                    mScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                    return true;
                case ONE_SHOT_MODE:
                    mScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
                    return true;
                default:
                    return false;
            }
        } catch (RemoteException ignored) { }
        return false;
    }

    public static boolean isCompatible() {
        return mCompatibleManufacturers.contains(Build.MANUFACTURER) && mCompatibleModels.contains(Build.MODEL);
    }

    @Override
    public void onResume(Context context) {
        context.registerReceiver(mResultReciever, resultFilter, Manifest.permission.SCANNER_RESULT_RECEIVER, null);
        context.registerReceiver(mScanKeyEventReceiver, new IntentFilter(ScanConst.INTENT_SCANKEY_EVENT));

        try {
            mScanner.aDecodeSetTriggerOn(0);
            mPreviousPrefix = mScanner.aDecodeGetPrefix();
            mPreviousPostfix = mScanner.aDecodeGetPostfix();
            mScanner.aDecodeSetPrefix("");
            mScanner.aDecodeSetPostfix("");

            if (mScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT) {
                setScanMode(ONE_SHOT_MODE);
            } else {
                setScanMode(CONTINUOUS_MODE);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause(Context context) {
        try {
            context.unregisterReceiver(mResultReciever);
        } catch (IllegalArgumentException ignored) { }

        try {
            context.unregisterReceiver(mScanKeyEventReceiver);
        } catch (IllegalArgumentException ignored) { }

        try {
            mScanner.aDecodeSetTriggerOn(0);
            mScanner.aDecodeSetPrefix(mPreviousPrefix);
            mScanner.aDecodeSetPostfix(mPreviousPostfix);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy(Context context) {
        try {
            context.unregisterReceiver(mResultReciever);
        } catch (IllegalArgumentException ignored) { }

        try {
            context.unregisterReceiver(mScanKeyEventReceiver);
        } catch (IllegalArgumentException ignored) { }

        try {
            mScanner.aDecodeSetTriggerOn(0);
            mScanner.aDecodeSetPrefix(mPreviousPrefix);
            mScanner.aDecodeSetPostfix(mPreviousPostfix);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
