package com.porterlee.limstransfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.io.File;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScanConst;
import device.scanner.ScannerService;

public class TransferActivity extends AppCompatActivity {
    public static final String TAG = TransferActivity.class.getName();
    public static final File EXTERNAL_PATH = new File("");//Environment.getExternalStorageDirectory(), "Transfer");
    public static final File OUTPUT_FILE = new File(EXTERNAL_PATH, "data.txt");
    public static final File SIGNATURE_FILE = new File(EXTERNAL_PATH, "signature.png");
    private CursorRecyclerViewAdapter<TransferItemViewHolder> mItemRecyclerAdapter;
    private volatile TransferDatabase mTransferDatabase;
    private long mSelectedLocationId = -1;
    private String mSelectedLocationBarcode = "";
    private String previousPrefix;
    private String previousPostfix;
    private IScannerService mScanner = null;
    private DecodeResult mDecodeResult = new DecodeResult();
    private ScanResultReceiver mResultReciever = new ScanResultReceiver();
    IntentFilter resultFilter = new IntentFilter();
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            initScanner();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        if (!EXTERNAL_PATH.mkdirs() && !EXTERNAL_PATH.exists())
            Log.w(TAG, "External directory does not exist and could not be created, this may cause a problem");

        try {
            mTransferDatabase = new TransferDatabase(this);
        } catch (SQLiteCantOpenDatabaseException e) {
            databaseLoadingError(new Runnable() {
                @Override
                public void run() {
                    init();
                }
            }, new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
        }

        init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_transfer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            //todo other menu options
            case R.id.menu_continuous_mode:
                try {
                    if (!item.isChecked()) {
                        mScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                        item.setChecked(true);
                    } else {
                        mScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_ONESHOT);
                        item.setChecked(false);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                    item.setChecked(false);
                    Toast.makeText(this, "An error occured while changing scanning mode", Toast.LENGTH_SHORT).show();
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem item = menu.findItem(R.id.menu_continuous_mode);
        if (mScanner != null) {
            try {
                if (mScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_AUTO) {
                    mScanner.aDecodeSetTriggerMode(ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                    item.setChecked(true);
                } else {
                    item.setChecked(mScanner.aDecodeGetTriggerMode() == ScannerService.TriggerMode.DCD_TRIGGER_MODE_CONTINUOUS);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            item.setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mResultReciever, resultFilter, Manifest.permission.SCANNER_RESULT_RECEIVER, null);
        registerReceiver(mScanKeyEventReceiver, new IntentFilter(ScanConst.INTENT_SCANKEY_EVENT));

        if (mScanner != null) {
            try {
                mScanner.aDecodeSetTriggerOn(0);
                previousPrefix = mScanner.aDecodeGetPrefix();
                previousPostfix = mScanner.aDecodeGetPostfix();
                mScanner.aDecodeSetPrefix("");
                mScanner.aDecodeSetPostfix("");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mResultReciever);
        unregisterReceiver(mScanKeyEventReceiver);

        if (mScanner != null) {
            try {
                mScanner.aDecodeSetTriggerOn(0);
                mScanner.aDecodeSetPrefix(previousPrefix);
                mScanner.aDecodeSetPostfix(previousPostfix);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void initScanner() throws RemoteException {
        if ((mScanner = IScannerService.Stub.asInterface(ServiceManager.getService("ScannerService"))) != null) {
            mScanner.aDecodeAPIInit();
            mScanner.aDecodeSetDecodeEnable(1);
            mScanner.aDecodeSetResultType(ScannerService.ResultType.DCD_RESULT_USERMSG);
        }
    }

    private void databaseLoadingError(final Runnable onDelete, final Runnable onFail) {
        AlertDialog.Builder builder = new AlertDialog.Builder(TransferActivity.this);
        builder.setCancelable(false);
        builder.setTitle("Database Load Error");
        builder.setMessage(
                "There was an error loading the last transfer file, Would you like to delete the it?\n" +
                "\n" +
                "Answering no will close the app."
        );
        builder.setNegativeButton("no", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) { finish(); }
        });
        builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (!mTransferDatabase.delete(TransferActivity.this) && mTransferDatabase.exists(TransferActivity.this)) {
                    Toast.makeText(TransferActivity.this, "The file could not be deleted", Toast.LENGTH_SHORT).show();
                    onFail.run();
                } else {
                    Toast.makeText(TransferActivity.this, "The file was deleted", Toast.LENGTH_SHORT).show();
                    onDelete.run();
                }
            }
        });
        builder.create().show();
    }

    private void init() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }

        setContentView(R.layout.activity_transfer);

        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(String.format("%1s v%2s", getString(R.string.app_name), BuildConfig.VERSION_NAME));

        //todo the rest of the setup

        this.<TextView>findViewById(R.id.current_location_text_view).setText("-");
        this.<TextView>findViewById(R.id.item_count_text_view).setText("-");

        /*this.<AppCompatButton>findViewById(R.id.test_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });*/
    }

    private static String cursorToString(Cursor cursor) {
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

    public static boolean csvContainsInt(String csv, int i) {
        final String regex = "(^" + i + ",.*)|(.*," + i + ",.*)|(.*," + i + "$)";
        return csv.matches(regex);
    }

    private void updateInfo(String location, int itemCount) {
        this.<TextView>findViewById(R.id.current_location_text_view).setText(BarcodeType.getBarcodeType(location).equals(BarcodeType.Location) ? location : "-");
        this.<TextView>findViewById(R.id.item_count_text_view).setText(itemCount >= 0 ? String.valueOf(itemCount) : "-");
    }

    private static boolean vibrate(@NotNull Context context) {
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

    private static void refreshExternalPath(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(EXTERNAL_PATH);
            mediaScanIntent.setData(contentUri);
            context.sendBroadcast(mediaScanIntent);
        } else {
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(EXTERNAL_PATH)));
        }
    }

    private class TransferItemViewHolder extends RecyclerView.ViewHolder {
        private long id;
        private String barcode;

        public TransferItemViewHolder(View itemView) {
            super(itemView);
            final AppCompatImageButton expandedMenuButton = itemView.findViewById(R.id.expanded_menu);
            expandedMenuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final PopupMenu popup = new PopupMenu(TransferActivity.this, expandedMenuButton);
                    popup.inflate(R.menu.menu_transfer_item);
                    popup.getMenu().findItem(R.id.menu_remove).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            if (mTransferDatabase.delete_from_itemTable_where_id_equals(id) <= 0) {
                                Log.w(TAG, String.format("Error deleting item with an id of %d from database", id));
                                Toast.makeText(TransferActivity.this, "Error removing item", Toast.LENGTH_SHORT).show();
                            }
                            return true;
                        }
                    });
                    popup.show();
                }
            });
        }

        public void bindViews(Cursor cursor) {
            id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            barcode = cursor.getString(cursor.getColumnIndexOrThrow("barcode"));
            itemView.<AppCompatTextView>findViewById(R.id.barcode_text_view).setText(barcode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (permissions.length != 0 && grantResults.length != 0) {
            if (requestCode == 1) {
                for (int i = 0; i < permissions.length; i++) {
                    if (permissions[i].equals(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        init();
                        return;
                    }
                }
                finish();
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public class ScanResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mScanner != null) {
                try {
                    mScanner.aDecodeGetResult(mDecodeResult);
                    String barcode = mDecodeResult.decodeValue;
                    if (!"READ FAIL".equals(mDecodeResult.symName))
                        onBarcodeScanned(barcode);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void onBarcodeScanned(String barcode) {
        //todo finish
    }
}
