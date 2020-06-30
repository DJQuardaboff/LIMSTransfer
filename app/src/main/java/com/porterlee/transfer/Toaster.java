package com.porterlee.transfer;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.StringRes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

public class Toaster {
    protected WeakReference<Activity> activity_weak;

    @IntDef(value = {
            Toast.LENGTH_SHORT,
            Toast.LENGTH_LONG
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ToastLength {}

    Toaster(Activity activity) { activity_weak = new WeakReference<>(activity); }

    public void toast(final String text, @ToastLength final int duration) {
        final Activity activity = activity_weak.get();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, duration).show();
                }
            });
        }
    }

    public void toast(@StringRes final int resId, @ToastLength final int length) {
        final Activity activity = activity_weak.get();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, resId, length).show();
                }
            });
        }
    }
}
