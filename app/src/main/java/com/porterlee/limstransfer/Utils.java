package com.porterlee.limstransfer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utils {

    public static void saveSignature(Context context, Bitmap signatureBitmap, File file) throws IOException {
        if (!file.getParentFile().mkdirs() && !file.getParentFile().exists())
            throw new IOException("Could not create signatures directory");

        try {
            saveBitmapToPNG(signatureBitmap, file);
            refreshExternalFile(context, file);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOException("Unable to save", e);
        }
    }

    public static void saveBitmapToPNG(Bitmap bitmap, File file) throws IOException {
        OutputStream stream = null;
        try {
            //Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            //Canvas canvas = new Canvas(newBitmap);
            //canvas.drawColor(Color.WHITE);
            //canvas.drawBitmap(bitmap, 0, 0, null);
            stream = new FileOutputStream(file);
            //newBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (stream != null) 
                stream.close();
        }
    }

    public static void refreshExternalFile(Context context, File file) {
        if (file.isDirectory()) {
            throw new IllegalArgumentException("Directories will be converted to files if refreshed");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            MediaScannerConnection.scanFile(context, new String[] { file.getAbsolutePath() }, null, null);
        } else {
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(file)));
        }
    }

    public static String cursorToString(Cursor cursor) {
        if (cursor.moveToFirst()) {
            StringBuilder result = new StringBuilder("Columns:");
            final String[] columnNames = cursor.getColumnNames();
            final int[] columnIndicies = new int[columnNames.length];
            for (int i = 0; i < columnNames.length; i++) {
                columnIndicies[i] = cursor.getColumnIndex(columnNames[i]);
                result.append('\"');
                result.append(columnNames[i]);
                result.append('\"');
                if (i < columnNames.length - 1)
                    result.append(',');
            }
            result.append("\r\n");

            while (!cursor.isAfterLast()) {
                for (int i = 0; i < columnIndicies.length; i++) {
                    result.append('\"');
                    result.append(cursor.getString(columnIndicies[i]));
                    result.append('\"');
                    if (i < columnIndicies.length - 1)
                        result.append(',');
                }
                result.append("\r\n");
                cursor.moveToNext();
            }
            return result.toString();
        }
        return null;
    }

    public static boolean csvContainsInt(@NotNull String csv, int i) {
        final String regex = "(^" + i + ",.*)|(.*," + i + ",.*)|(.*," + i + "$)";
        return csv.replace(" ", "").matches(regex);
    }

    public static class Toaster {
        protected WeakReference<Activity> activityWeakReference;

        Toaster(Activity activity) { activityWeakReference = new WeakReference<>(activity); }

        public boolean toast(final String s) {
            if (activityWeakReference.get() != null) {
                activityWeakReference.get().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activityWeakReference.get(), s, Toast.LENGTH_SHORT).show();
                    }
                });
                return true;
            }
            return false;
        }
    }

    public static char byteToHexChar(byte num) {
        //num = (byte) (num & 0x0F);
        return (char) ((num = (byte) (num & 0x0F)) + (num < 10 ? 48 : 55));
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            str.append(byteToHexChar((byte) (bytes[i] >> 4)));
            str.append(byteToHexChar(bytes[i]));
        }
        return str.toString();
    }

    public static String SHA1(String text) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(text.getBytes("US-ASCII"), 0, text.length());
        return bytesToHex(md.digest());
    }

    public static class Holder <T> {
        private T object;

        public T get() {
            return object;
        }

        public void set(T object) {
            this.object = object;
        }
    }
    
    public interface OnProgressUpdateListener {
        void onProgressUpdate(float progress);
    }

    public interface OnFinishListener {
        void onFinish(boolean success);
    }
    
    public static class QueryHolder {
        private SQLiteDatabase mDatabase;
        private String mQuery;
        private String[] mArgs;
        
        public QueryHolder(SQLiteDatabase database, String query, String... args) {
            mDatabase = database;
            mQuery = query;
            mArgs = args;
        }
        
        public Cursor query() {
            return mDatabase.rawQuery(mQuery, mArgs);
        }
    }
}
