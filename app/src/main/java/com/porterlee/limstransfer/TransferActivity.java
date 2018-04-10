package com.porterlee.limstransfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatDialogFragment;
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

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScanConst;
import device.scanner.ScannerService;

public class TransferActivity extends AppCompatActivity {
    public static final String TAG = TransferActivity.class.getName();
    public static final File EXTERNAL_PATH = new File(Environment.getExternalStorageDirectory(), "Transfer");
    public static final File OUTPUT_FILE = new File(EXTERNAL_PATH, "data.txt");
    public static final File SIGNATURE_FILE = new File(EXTERNAL_PATH, "signature.png");
    private SelectableCursorRecyclerViewAdapter<TransferItemViewHolder> mItemRecyclerAdapter;
    private volatile TransferDatabase mTransferDatabase;
    private Location mSelectedLocation;
    private String previousPrefix;
    private String previousPostfix;
    private IScannerService mScanner = null;
    private DecodeResult mDecodeResult = new DecodeResult();
    private BroadcastReceiver mResultReciever = new BroadcastReceiver() {
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
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mTransferDatabase != null && mTransferDatabase.isOpen())
            mTransferDatabase.close();

        if (mItemRecyclerAdapter != null && mItemRecyclerAdapter.getCursor() != null)
            mItemRecyclerAdapter.getCursor().close();

        if (mScanner != null) {
            try {
                mScanner.aDecodeSetTriggerOn(0);
                mScanner.aDecodeSetPrefix(previousPrefix);
                mScanner.aDecodeSetPostfix(previousPostfix);
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
            if (!mTransferDatabase.delete(TransferActivity.this) && mTransferDatabase.exists(TransferActivity.this)) {
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
        mItemRecyclerAdapter = new SelectableCursorRecyclerViewAdapter<TransferItemViewHolder>(mTransferDatabase.getItemListCursor(), TransferDatabase.ItemTable.Key.ID) {
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

        this.<TextView>findViewById(R.id.current_location_text_view).setText("-");
        this.<TextView>findViewById(R.id.item_count_text_view).setText("-");

        this.<AppCompatButton>findViewById(R.id.test_button).setOnClickListener(v -> {

        });
    }

    private void showSignatureDialog() {
        //FragmentManager fm = getSupportFragmentManager();
        //AppCompatDialogFragment editNameDialogFragment = AppCompatDialogFragment.newInstance("Some Title");
        //editNameDialogFragment.show(fm, "fragment_edit_name");

        //final View signatureLayout = View.inflate(this, R.layout.fragment_sign, null);
        //final SignaturePad signaturepad = signatureLayout.findViewById(R.id.signature_pad);
        /*try {
            saveSignature(signaturepad.getSignatureBitmap());
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(TransferActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        (dialog, which) -> signaturepad.clear()
        (dialog, which) -> dialog.dismiss()
        */

        //signatureDialogBuilder.setView(signatureLayout);
        //final AlertDialog signatureDialog = signatureDialogBuilder.create();

        /*signaturepad.setOnSignedListener(new SignaturePad.OnSignedListener() {
            @Override
            public void onStartSigning() { }

            @Override
            public void onSigned() {
                signatureDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                signatureDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(true);

                Log.e(TAG, "dialog height: " + signatureDialog.getWindow().getAttributes().height);
                //WindowManager.LayoutParams.
            }

            @Override
            public void onClear() {
                signatureDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                signatureDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(false);
            }
        });*/

        //signatureDialog.show();
    }

    public void saveSignature(Bitmap signatureBitmap) throws IOException {
        if (!SIGNATURE_FILE.getParentFile().mkdirs() && !SIGNATURE_FILE.getParentFile().exists())
            throw new IOException("Could not create signatures directory");

        try {
            saveBitmapToPNG(signatureBitmap, SIGNATURE_FILE);
            refreshExternalPath(this, SIGNATURE_FILE);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOException("Unable to save", e);
        }
    }

    public static void saveBitmapToPNG(Bitmap bitmap, File file) throws IOException {
        Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(bitmap, 0, 0, null);
        OutputStream stream = new FileOutputStream(file);
        newBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        stream.close();
    }

    /*private static String cursorToString(Cursor cursor) {
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
    }*/

    public static boolean csvContainsInt(String csv, int i) {
        final String regex = "(^" + i + ",.*)|(.*," + i + ",.*)|(.*," + i + "$)";
        return csv.replace(" ", "").matches(regex);
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

    private static void refreshExternalPath(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(file);
            mediaScanIntent.setData(contentUri);
            context.sendBroadcast(mediaScanIntent);
        } else {
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.fromFile(file)));
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

    private void onBarcodeScanned(String barcode) {
        //todo finish
    }

    private class TransferItemViewHolder extends RecyclerView.ViewHolder {
        private long id;
        private String barcode;

        public TransferItemViewHolder(ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transfer, parent, false));
            final AppCompatImageButton expandedMenuButton = itemView.findViewById(R.id.expanded_menu);
            expandedMenuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final PopupMenu popup = new PopupMenu(TransferActivity.this, expandedMenuButton);
                    popup.inflate(R.menu.item_transfer);
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

    private class Location {
        public long id;
        public String barcode;
    }

    private class Item {
        public long id;
        public long locationId;
        public String barcode;
    }
}
