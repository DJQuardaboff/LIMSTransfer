package com.porterlee.limstransfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.github.gcacace.signaturepad.views.SignaturePad;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Objects;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScanConst;
import device.scanner.ScannerService;

import static com.porterlee.limstransfer.DataManager.BarcodeType.Location;
import static com.porterlee.limstransfer.DataManager.BarcodeType.getBarcodeType;
import static com.porterlee.limstransfer.DataManager.BarcodeType;

public class TransferActivity extends AppCompatActivity {
    public static final String TAG = TransferActivity.class.getName();
    public static final File EXTERNAL_PATH = new File(Environment.getExternalStorageDirectory(), "Transfer");
    public static final File SIGNATURES_PATH = new File(EXTERNAL_PATH, "Signatures");
    public static final File OUTPUT_FILE = new File(EXTERNAL_PATH, "data.txt");
    public static final String SIGNATURE_FILE_NAME = "signature_%d.png";
    private SelectableCursorRecyclerViewAdapter<TransferItemViewHolder> mItemRecyclerAdapter;
    private DataManager mDataManager;
    private IScannerService mScanner = null;
    private final DecodeResult mDecodeResult = new DecodeResult();
    private final BroadcastReceiver mResultReciever = new BroadcastReceiver() {
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
            mDataManager = new DataManager(this);
        } catch (SQLiteCantOpenDatabaseException e) {
            databaseLoadingError(this::init, this::finish);
        }

        init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_transfer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_sign:
                //todo start sign activity
                showSignatureDialog();
                return true;
            case R.id.menu_reset:
                //todo reset transfer list
                return true;
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
                mDataManager.setPreviousPrefix(mScanner.aDecodeGetPrefix());
                mDataManager.setPreviousPostfix(mScanner.aDecodeGetPostfix());
                mScanner.aDecodeSetPrefix("");
                mScanner.aDecodeSetPostfix("");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mResultReciever);
        unregisterReceiver(mScanKeyEventReceiver);

        if (mScanner != null) {
            try {
                mScanner.aDecodeSetTriggerOn(0);
                mScanner.aDecodeSetPrefix(mDataManager.getPreviousPrefix());
                mScanner.aDecodeSetPostfix(mDataManager.getPreviousPostfix());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mDataManager.getDatabase() != null && mDataManager.getDatabase().isOpen())
            mDataManager.getDatabase().close();

        if (mItemRecyclerAdapter != null && mItemRecyclerAdapter.getCursor() != null)
            mItemRecyclerAdapter.getCursor().close();

        if (mScanner != null) {
            try {
                mScanner.aDecodeSetTriggerOn(0);
                mScanner.aDecodeSetPrefix(mDataManager.getPreviousPrefix());
                mScanner.aDecodeSetPostfix(mDataManager.getPreviousPostfix());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
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
        builder.setNegativeButton("no", (dialog, which) -> finish());
        builder.setPositiveButton("yes", (dialog, which) -> {
            if (!mDataManager.getDatabase().delete(TransferActivity.this) && mDataManager.getDatabase().exists(TransferActivity.this)) {
                Toast.makeText(TransferActivity.this, "The file could not be deleted", Toast.LENGTH_SHORT).show();
                onFail.run();
            } else {
                Toast.makeText(TransferActivity.this, "The file was deleted", Toast.LENGTH_SHORT).show();
                onDelete.run();
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

        final RecyclerView itemRecyclerView = findViewById(R.id.item_recycler_view);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mItemRecyclerAdapter = new SelectableCursorRecyclerViewAdapter<TransferItemViewHolder>(mDataManager.getDatabase().getItemListCursor(), TransferDatabase.ItemTable.Key.ID) {
            @Override
            public void onBindViewHolder(TransferItemViewHolder viewHolder, Cursor cursor) {
                viewHolder.bindViews(cursor);
            }

            @NonNull
            @Override
            public TransferItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new TransferItemViewHolder(parent);
            }
        };
        itemRecyclerView.setAdapter(mItemRecyclerAdapter);
        itemRecyclerView.setHasFixedSize(true);
        final RecyclerView.ItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setAddDuration(100);
        itemAnimator.setChangeDuration(0);
        itemAnimator.setMoveDuration(100);
        itemAnimator.setRemoveDuration(100);
        itemRecyclerView.setItemAnimator(itemAnimator);
        final DividerItemDecoration itemDecoration = new DividerItemDecoration(this, DividerItemDecoration.HORIZONTAL);
        itemDecoration.setDrawable(Objects.requireNonNull(ContextCompat.getDrawable(this, R.drawable.divider_item_transfer)));
        itemRecyclerView.addItemDecoration(itemDecoration);

        this.<TextView>findViewById(R.id.text_current_location).setText("-");
        this.<TextView>findViewById(R.id.text_item_count).setText("-");

        this.<AppCompatButton>findViewById(R.id.test_button).setOnClickListener(v -> {

        });
    }

    private void showSignatureDialog() {
        final AppCompatDialog compatDialog = new AppCompatDialog(this, R.style.CustomDialogTheme);
        Objects.requireNonNull(compatDialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        compatDialog.setCancelable(false);
        compatDialog.setContentView(R.layout.fragment_sign);

        View v = compatDialog.getWindow().getDecorView();
        final SignaturePad signaturePad = v.findViewById(R.id.signature_pad);
        final AppCompatButton buttonClear = v.findViewById(R.id.button_clear);
        final AppCompatButton buttonCancel = v.findViewById(R.id.button_cancel);
        final AppCompatButton buttonSave = v.findViewById(R.id.button_save);

        signaturePad.setOnSignedListener(new SignaturePad.OnSignedListener() {
            @Override
            public void onStartSigning() { }

            @Override
            public void onSigned() {
                buttonClear.setEnabled(true);
                buttonSave.setEnabled(true);
            }

            @Override
            public void onClear() {
                buttonClear.setEnabled(false);
                buttonSave.setEnabled(false);
            }
        });

        buttonClear.setOnClickListener(v1 -> signaturePad.clear());
        buttonCancel.setOnClickListener(v1 -> compatDialog.dismiss());
        buttonSave.setOnClickListener(v1 -> {
            mDataManager.setSignature(signaturePad.getSignatureBitmap());
            save();
            compatDialog.dismiss();
        });

        compatDialog.show();
    }

    private void save() {
        Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).show();

        File tempSignature;

        try {
            tempSignature = File.createTempFile("signature", ".png", EXTERNAL_PATH);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Unable to save", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Utils.saveSignature(this, mDataManager.getSignature(), tempSignature);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        //todo finish
        saveTransferToFile();

        Log.e(TAG, String.valueOf(mDataManager.getTransferId()));

        mDataManager.setCurrentLocation(mDataManager.newLocation("VAN  MIKE    "));

        Log.e(TAG, String.valueOf(mDataManager.getTransferId()));

        if (!tempSignature.renameTo(new File(SIGNATURES_PATH, String.format(Locale.US, SIGNATURE_FILE_NAME, mDataManager.getTransferId())))) {
            Toast.makeText(this, "Could not rename file", Toast.LENGTH_SHORT).show();
        }

        Utils.refreshExternalPath(this, EXTERNAL_PATH);

        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
    }

    public static void saveTransferToFile() {
        Class<?> c = new Object() { }.getClass();
        Log.e(TAG, String.valueOf(Modifier.isStatic(c.getModifiers())));
        //todo finish
    }
    /*
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
    */
    private void updateInfo(String location, int itemCount) {
        this.<TextView>findViewById(R.id.text_current_location).setText(getBarcodeType(location).equals(DataManager.BarcodeType.Location) ? location : "-");
        this.<TextView>findViewById(R.id.text_item_count).setText(itemCount >= 0 ? String.valueOf(itemCount) : "-");
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

    private void onBarcodeScanned(String barcode) {
        if (BarcodeType.Location.isOfType(barcode))
            startNewTransfer(barcode);
        //todo finish
    }

    private void startNewTransfer(String locationBarcode) {

    }

    private class TransferItemViewHolder extends RecyclerView.ViewHolder {
        private long id;
        private String barcode;

        public TransferItemViewHolder(ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transfer, parent, false));
            final AppCompatImageButton expandedMenuButton = itemView.findViewById(R.id.expanded_menu);
            expandedMenuButton.setOnClickListener(v -> {
                final PopupMenu popup = new PopupMenu(TransferActivity.this, expandedMenuButton);
                popup.inflate(R.menu.item_transfer);
                popup.getMenu().findItem(R.id.menu_remove).setOnMenuItemClickListener(menuItem -> {
                    if (mDataManager.getDatabase().delete_from_itemTable_where_id_equals(id) <= 0) {
                        Log.w(TAG, String.format("Error deleting item with an id of %d from database", id));
                        Toast.makeText(TransferActivity.this, "Error removing item", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                });
                popup.show();
            });
        }

        public void bindViews(Cursor cursor) {
            id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            barcode = cursor.getString(cursor.getColumnIndexOrThrow("barcode"));
            itemView.<AppCompatTextView>findViewById(R.id.barcode_text_view).setText(barcode);
        }
    }
}
