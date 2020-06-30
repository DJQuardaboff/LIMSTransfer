package com.porterlee.transfer;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
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
            stream = new FileOutputStream(file);
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

    public static boolean csvContainsInt(@NonNull String csv, int i) {
        final String regex = "(^" + i + ",.*)|(.*," + i + ",.*)|(.*," + i + "$)";
        return csv.replace(" ", "").matches(regex);
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
        md.update(text.getBytes(StandardCharsets.US_ASCII), 0, text.length());
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
        void onProgressUpdate(@FloatRange(from = 0.0f, to = 1.0f) float progress);
    }

    public interface OnFinishListener {
        void onFinish(boolean success);
    }

    public interface DetailedOnFinishListener {
        void onFinish(boolean success, String message);
    }

    public static Item constructItemFromCursor(Cursor cursor) {
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        return new Item(
                cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.ID)),
                cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.TRANSFER_ID)),
                cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.BARCODE)),
                cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.QUANTITY)),
                cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.SCAN_DATETIME))
        );
    }

    public static Transfer constructTransferFromCursor(Cursor cursor) {
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        return new Transfer(
                cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.ID)),
                cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.BATCH_ID)),
                cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.LOCATION_BARCODE)),
                cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.SIGNED)) != 0,
                cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.FINALIZED)) != 0,
                cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.CANCELED)) != 0,
                cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.SAVED)) != 0,
                cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.START_DATETIME)),
                cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.SIGN_DATETIME)),
                cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.FINALIZE_DATETIME)),
                cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.CANCEL_DATETIME)),
                cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.SAVE_DATETIME)),
                cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.COMMENTS))
        );
    }

    public static Batch constructBatchFromCursor(Cursor cursor) {
        if (cursor.getCount() <= 0)
            return null;
        cursor.moveToFirst();
        return new Batch(
                cursor.getLong(cursor.getColumnIndex(TransferDatabase.Key.ID)),
                cursor.getString(cursor.getColumnIndex(TransferDatabase.Key.START_DATETIME))
        );
    }

    public static class Item {
        public final long id;
        public final long transfer_id;
        public final String barcode;
        public final long quantity;
        public final String scan_datetime;

        private Item(
                long id,
                long transfer_id,
                String barcode,
                long quantity,
                String scan_datetime
        ) {
            this.id = id;
            this.transfer_id = transfer_id;
            this.barcode = barcode;
            this.quantity = quantity;
            this.scan_datetime = scan_datetime;
        }
    }

    public static class Transfer {
        public final long id;
        public final Long batch_id;
        public final String location_barcode;
        public final boolean signed;
        public final boolean finalized;
        public final boolean canceled;
        public final Boolean saved;
        public final String start_datetime;
        public final String sign_datetime;
        public final String finalize_datetime;
        public final String cancel_datetime;
        public final String save_datetime;
        public final String comments;

        public Transfer(
                long id,
                long batch_id,
                String location_barcode,
                boolean signed,
                boolean finalized,
                boolean canceled,
                boolean saved,
                String start_datetime,
                String sign_datetime,
                String finalize_datetime,
                String cancel_datetime,
                String save_datetime,
                String comments
        ) {
            this.id = id;
            this.batch_id = batch_id;
            this.location_barcode = location_barcode;
            this.signed = signed;
            this.finalized = finalized;
            this.canceled = canceled;
            this.saved = saved;
            this.start_datetime = start_datetime;
            this.sign_datetime = sign_datetime;
            this.finalize_datetime = finalize_datetime;
            this.cancel_datetime = cancel_datetime;
            this.save_datetime = save_datetime;
            this.comments = comments;
        }

        public boolean isActive() {
            return !finalized && !canceled;
        }
    }

    public static class Batch {
        public final long id;
        public final String start_datetime;

        public Batch(
                long id,
                String start_datetime
        ) {
            this.id = id;
            this.start_datetime = start_datetime;
        }
    }
}
