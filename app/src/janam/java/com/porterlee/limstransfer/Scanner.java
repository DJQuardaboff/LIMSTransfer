package com.porterlee.limstransfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.RemoteException;

import androidx.annotation.NonNull;

import android.util.Log;
import android.view.KeyEvent;

import device.common.DecodeResult;
import device.common.ScanConst;
import device.sdk.ScanManager;

//import com.porterlee.standardinventory.ScannerUtils;

import java.util.HashSet;

public class Scanner {
    private static final String TAG = Scanner.class.getCanonicalName();
    private static final String READ_FAIL_SYMBOL = "READ_FAIL";

    private Scanner() { }
    private static Scanner mInstance;
    @NonNull
    public static Scanner getInstance() {
        return mInstance != null ? mInstance : (mInstance = new Scanner());
    }

    private boolean mIsEnabled = true;
    private DecodeResult mDecodeResult = null;
    private HashSet<Integer> mScannerKeysDown = new HashSet<>();

    private final IntentFilter SCAN_RESULT_EVENT_FILTER = new IntentFilter(ScanConst.INTENT_USERMSG);
    private final BroadcastReceiver SCAN_RESULT_EVENT_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (mDecodeResult != null) {
                    ScanManager.getInstance().aDecodeGetResult(mDecodeResult.recycle());
                    if (!READ_FAIL_SYMBOL.equals(mDecodeResult.symName)) {
                        String barcode = mDecodeResult.toString();
                        ScannerUtils.getInstance().onBarcodeScanned(barcode == null ? "" : barcode);
                    }
                }
            } catch (SecurityException | NoClassDefFoundError e) {
                e.printStackTrace();
            }
        }
    };

    private final IntentFilter SCAN_KEY_EVENT_FILTER = new IntentFilter(ScanConst.INTENT_USERMSG);
    private final BroadcastReceiver SCAN_KEY_EVENT_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ScanConst.INTENT_SCANKEY_EVENT.equals(intent.getAction())) {
                final KeyEvent event = intent.getParcelableExtra(ScanConst.EXTRA_SCANKEY_EVENT);

                final int keycode = event.getKeyCode();
                switch (keycode) {
                    case ScanConst.KEYCODE_SCAN_FRONT:
                    case ScanConst.KEYCODE_SCAN_LEFT:
                    case ScanConst.KEYCODE_SCAN_RIGHT:
                    case ScanConst.KEYCODE_SCAN_REAR:
                        switch (event.getAction()) {
                            case KeyEvent.ACTION_DOWN:
                                mScannerKeysDown.add(keycode);
                                break;
                            case KeyEvent.ACTION_UP:
                                mScannerKeysDown.remove(keycode);
                                break;
                        }
                        break;
                }

                try {
                    if (mScannerKeysDown.isEmpty()) {
                        ScanManager.getInstance().aDecodeSetTriggerOn(0);
                    } else {
                        ScanManager.getInstance().aDecodeSetTriggerOn(1);
                    }
                } catch (SecurityException | NoClassDefFoundError e) {
                    e.printStackTrace();
                }
            }
        }
    };

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
            ScanManager.getInstance().aDecodeSetTriggerEnable(1);
        } catch (SecurityException | NoClassDefFoundError e) {
            throw new RuntimeException(e);
        }
    }

    public void disable() {
        try {
            ScanManager.getInstance().aDecodeSetTriggerEnable(0);
        } catch (SecurityException | NoClassDefFoundError e) {
            throw new RuntimeException(e);
        }
    }

    public void init() { }

    public void start() {
        if (mDecodeResult == null) {
            try {
                mDecodeResult = new DecodeResult();
            } catch (NoClassDefFoundError ignored) { }
            if (mDecodeResult == null) {
                throw new NullPointerException("start() - mDecodeResult is null!");
            }
        }

        mScannerKeysDown.clear();

        Context context = ScannerUtils.getInstance().getApplicationContext();

        if (context == null) {
            throw new NullPointerException("start() - context is null!");
        }

        context.registerReceiver(SCAN_RESULT_EVENT_RECEIVER, SCAN_RESULT_EVENT_FILTER);
        context.registerReceiver(SCAN_KEY_EVENT_RECEIVER, SCAN_KEY_EVENT_FILTER);

        try {
            ScanManager scanner = ScanManager.getInstance();

            scanner.aDecodeSetTriggerOn(0);
            scanner.aDecodeSetBeepEnable(0);
            scanner.aDecodeSetVibratorEnable(0);
            scanner.aDecodeSetDecodeEnable(1);
            scanner.aDecodeSetTerminator(ScanConst.Terminator.DCD_TERMINATOR_NONE);
            scanner.aDecodeSetResultType(ScanConst.ResultType.DCD_RESULT_USERMSG);
            scanner.aDecodeSetTriggerMode(ScanConst.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
            scanner.aDecodeSetPrefixEnable(0);
            scanner.aDecodeSetPostfixEnable(0);
            setIsEnabled(mIsEnabled);
        } catch (SecurityException | NoClassDefFoundError e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        Context context = ScannerUtils.getInstance().getApplicationContext();

        if (context == null) {
            throw new NullPointerException("stop() - context is null!");
        }

        try {
            context.unregisterReceiver(SCAN_RESULT_EVENT_RECEIVER);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        }

        try {
            context.unregisterReceiver(SCAN_KEY_EVENT_RECEIVER);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        }

        try {
            ScanManager scanner = ScanManager.getInstance();

            scanner.aDecodeSetTriggerEnable(1);
            scanner.aDecodeSetTriggerOn(0);
        } catch (SecurityException | NoClassDefFoundError e) {
            throw new RuntimeException(e);
        }
    }

    public void release() { }

    public boolean isReady() {
        return mDecodeResult != null;
    }

    @NonNull
    public String[] getPermissions() {
        return new String[0];
    }
}
