package com.porterlee.limstransfer.Scanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.KeyEvent;

import com.porterlee.limstransfer.Manifest;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScanConst;
import device.scanner.ScannerService;

public class Scanner extends AbstractScanner {
    public static final String TAG = Scanner.class.getSimpleName();
    private static final String READ_FAIL_SYMBOL = "READ FAIL";
    private IScannerService mScanner;
    private String mPreviousPrefix;
    private String mPreviousPostfix;
    private final DecodeResult mDecodeResult = new DecodeResult();
    private final IntentFilter resultFilter = new IntentFilter("device.scanner.USERMSG");
    private final BroadcastReceiver mResultReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                mScanner.aDecodeGetResult(mDecodeResult);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            if (mScanner != null) {
                if (!READ_FAIL_SYMBOL.equals(mDecodeResult.symName))
                    onBarcodeScanned(mDecodeResult.decodeValue);
            }
        }
    };

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
    public boolean init(Context context) {
        if ((mScanner = IScannerService.Stub.asInterface(ServiceManager.getService("ScannerService"))) != null) {
            try {
                mScanner.aDecodeAPIInit();
                mScanner.aDecodeSetDecodeEnable(1);
                mScanner.aDecodeSetResultType(ScannerService.ResultType.DCD_RESULT_USERMSG);
                return true;
            } catch (RemoteException ignored) {
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onScanModeChanged(int scanMode) {
        if (mScanner != null) {
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
        }
        return false;
    }

    @Override
    public void onResume(Context context) {
        if (context != null) {
            context.registerReceiver(mResultReciever, resultFilter, Manifest.permission.SCANNER_RESULT_RECEIVER, null);
            context.registerReceiver(mScanKeyEventReceiver, new IntentFilter(ScanConst.INTENT_SCANKEY_EVENT));
        }

        if (mScanner != null) {
            try {
                mScanner.aDecodeSetTriggerOn(0);
                swapPrefixAndPostfix(true);
                getTriggerMode();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void swapPrefixAndPostfix(boolean forOurUse) throws RemoteException {
        if (mScanner != null) {
            if (!forOurUse) {
                mPreviousPrefix = mScanner.aDecodeGetPrefix();
                mPreviousPostfix = mScanner.aDecodeGetPostfix();
            }
            mScanner.aDecodeSetPrefix(forOurUse ? "" : mPreviousPrefix);
            mScanner.aDecodeSetPostfix(forOurUse ? "" : mPreviousPostfix);
        }
    }

    private void getTriggerMode() throws RemoteException {
        if (mScanner != null) {
            if (mScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT) {
                setScanMode(ONE_SHOT_MODE);
            } else {
                setScanMode(CONTINUOUS_MODE);
            }
        }
    }

    @Override
    public void onPause(Context context) {
        if (context != null) {
            try {
                context.unregisterReceiver(mResultReciever);
            } catch (IllegalArgumentException ignored) { }

            try {
                context.unregisterReceiver(mScanKeyEventReceiver);
            } catch (IllegalArgumentException ignored) { }
        }

        if (mScanner != null) {
            try {
                mScanner.aDecodeSetTriggerOn(0);
                swapPrefixAndPostfix(false);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDestroy(Context context) {
        if (context != null) {
            try {
                context.unregisterReceiver(mResultReciever);
            } catch (IllegalArgumentException ignored) { }

            try {
                context.unregisterReceiver(mScanKeyEventReceiver);
            } catch (IllegalArgumentException ignored) { }
        }

        if (mScanner != null) {
            try {
                mScanner.aDecodeSetTriggerOn(0);
                mScanner.aDecodeSetPrefix(mPreviousPrefix);
                mScanner.aDecodeSetPostfix(mPreviousPostfix);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
