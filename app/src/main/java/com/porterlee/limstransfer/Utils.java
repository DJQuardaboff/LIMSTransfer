package com.porterlee.limstransfer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static android.content.Context.VIBRATOR_SERVICE;

public class Utils {

    public static boolean vibrate(@NotNull Context context) {
        final Vibrator vibrator = (Vibrator) context.getSystemService(VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator != null) {
                vibrator.vibrate(VibrationEffect.createOneShot(300L, VibrationEffect.DEFAULT_AMPLITUDE));
                return true;
            }
        } else {
            if (vibrator != null) {
                vibrator.vibrate(300L);
                return true;
            }
        }
        return false;
    }

    public static void saveSignature(Context context, Bitmap signatureBitmap, File file) throws IOException {
        if (!file.getParentFile().mkdirs() && !file.getParentFile().exists())
            throw new IOException("Could not create signatures directory");

        try {
            saveBitmapToPNG(signatureBitmap, file);
            refreshExternalPath(context, file);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOException("Unable to save", e);
        }
    }

    public static void saveBitmapToPNG(Bitmap bitmap, File file) throws IOException {
        //Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        //Canvas canvas = new Canvas(newBitmap);
        //canvas.drawColor(Color.WHITE);
        //canvas.drawBitmap(bitmap, 0, 0, null);
        OutputStream stream = new FileOutputStream(file);
        //newBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        stream.close();
    }

    public static void refreshExternalPath(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(file);
            mediaScanIntent.setData(contentUri);
            context.sendBroadcast(mediaScanIntent);
        } else {
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(file)));
        }
    }

    public static boolean csvContainsInt(@NotNull String csv, int i) {
        final String regex = "(^" + i + ",.*)|(.*," + i + ",.*)|(.*," + i + "$)";
        return csv.replace(" ", "").matches(regex);
    }
}
