package com.porterlee.limstransfer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class ScannerUtils {
    private static final String TAG = ScannerUtils.class.getCanonicalName();
    private static final String PREFERENCES_NAME = "plc_scanner_preferences";
    private static final String FIRST_TIME_SCANNER_SETUP_KEY = "first_time_setup_" + BuildConfig.FLAVOR;

    private ScannerUtils() { }
    private static ScannerUtils mInstance;
    @NonNull
    public static ScannerUtils getInstance() {
        return mInstance != null ? mInstance : (mInstance = new ScannerUtils());
    }

    private long[] mVibrationPattern = { 0L, 150L, 100L, 150L };
    private MediaPlayer mScanFailMediaPlayer;
    private MediaPlayer mScanSuccessMediaPlayer;
    private OnBarcodeScannedListener mOnBarcodeScannedListener;
    private WeakReference<Activity> mCurrentActivity;
    private boolean mFirstTimeScannerSetup;



    public void setActivity(Activity activity) {
        if (mCurrentActivity != null && mCurrentActivity.get() == activity) {
            return;
        }

        mCurrentActivity = new WeakReference<>(activity);
        if (getApplicationContext() != null) {
            SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            mFirstTimeScannerSetup = sharedPreferences.getBoolean(FIRST_TIME_SCANNER_SETUP_KEY, true);

            if (mFirstTimeScannerSetup) {
                sharedPreferences.edit().putBoolean(FIRST_TIME_SCANNER_SETUP_KEY, false).apply();
            }

            if (mScanSuccessMediaPlayer != null) {
                mScanSuccessMediaPlayer.release();
            }
            mScanSuccessMediaPlayer = new MediaPlayer();
            try {
                mScanSuccessMediaPlayer.setDataSource(getApplicationContext(), Uri.parse("android.resource://" + getApplicationContext().getPackageName() + "/" + R.raw.scan_success));
                if (Build.VERSION.SDK_INT >= 21) {
                    mScanSuccessMediaPlayer.setAudioAttributes(
                            new AudioAttributes
                                    .Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                    .build()
                    );
                } else {
                    mScanSuccessMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
                }
                mScanSuccessMediaPlayer.prepareAsync();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "scan_success.wav could not be initialized");
            }

            if (mScanFailMediaPlayer != null) {
                mScanFailMediaPlayer.release();
            }
            mScanFailMediaPlayer = new MediaPlayer();
            try {
                mScanFailMediaPlayer.setDataSource(getApplicationContext(), Uri.parse("android.resource://" + getApplicationContext().getPackageName() + "/" + R.raw.scan_fail));
                if (Build.VERSION.SDK_INT >= 21) {
                    mScanFailMediaPlayer.setAudioAttributes(
                            new AudioAttributes
                                    .Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                    .build()
                    );
                } else {
                    mScanFailMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
                }
                mScanFailMediaPlayer.prepareAsync();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "scan_fail.wav could not be initialized");
            }
        }
    }

    @Nullable
    public Activity getActivity() {
        return mCurrentActivity != null ? mCurrentActivity.get() : null;
    }

    @Nullable
    public Context getApplicationContext() {
        return mCurrentActivity != null ? (mCurrentActivity.get() != null ? mCurrentActivity.get().getApplicationContext() : null) : null;
    }

    public void onScanComplete(boolean success) {
        if (success) {
            mScanSuccessMediaPlayer.start();
        } else {
            mScanFailMediaPlayer.start();
            if (getApplicationContext() != null)
                vibrate(getApplicationContext());
        }
    }

    public void setVibrationPattern(long[] vibrationPattern) {
        vibrationPattern = vibrationPattern;
    }

    public boolean vibrate(@NonNull Context context) {
        final Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            if (vibrator != null) {
                vibrator.vibrate(VibrationEffect.createWaveform(mVibrationPattern, -1));
                return true;
            }
        } else {
            if (vibrator != null) {
                vibrator.vibrate(mVibrationPattern, -1);
                return true;
            }
        }
        return false;
    }

    public interface OnBarcodeScannedListener {
        void onBarcodeScanned(String barcode);
    }

    public void setOnBarcodeScannedListener(OnBarcodeScannedListener onBarcodeScannedListener) {
        mOnBarcodeScannedListener = onBarcodeScannedListener;
    }

    public OnBarcodeScannedListener getOnBarcodeScannedListener() {
        return mOnBarcodeScannedListener;
    }

    public void onBarcodeScanned(final String barcode) {
        if (mOnBarcodeScannedListener != null && getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mOnBarcodeScannedListener.onBarcodeScanned(barcode);
                }
            });
        }
    }
}
