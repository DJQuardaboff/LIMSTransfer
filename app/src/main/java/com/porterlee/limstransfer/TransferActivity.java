package com.porterlee.limstransfer;

import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.github.gcacace.signaturepad.views.SignaturePad;
import com.porterlee.limstransfer.Scanner.AbstractScanner;

import java.util.Objects;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

import static com.porterlee.limstransfer.DataManager.BarcodeType.getBarcodeType;
import static com.porterlee.limstransfer.DataManager.BarcodeType;

public class TransferActivity extends AppCompatActivity {
    public static final String TAG = TransferActivity.class.getCanonicalName();
    private SelectableCursorRecyclerViewAdapter<TransferItemViewHolder> mItemRecyclerAdapter;
    private DataManager mDataManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!AbstractScanner.isCompatible()) {
            Log.w(TAG, "Cannot guarantee app will work");
        }

        mDataManager = new DataManager();

        if (!mDataManager.initScanner(this)) {
            finish();
            Toast.makeText(this, "Scanner failed to initialize", Toast.LENGTH_SHORT).show();
            return;
        }

        setContentView(R.layout.activity_transfer);

        mDataManager.getScanner().setOnBarcodeScannedListener(barcode -> {
            if (mDataManager == null)
                return;
            if (BarcodeType.Location.isOfType(barcode)) {
                if (mDataManager.getItemCount() <= 0) {
                    if (mDataManager.getCurrentTransfer() != null && !mDataManager.getCurrentTransfer().finalized) {
                        mDataManager.cancelCurrentTransfer();
                    }
                    mDataManager.newTransfer(barcode);
                } else {
                    Toast.makeText(this, "Finalize or cancel this transfer first", Toast.LENGTH_SHORT).show();
                }
            } else if (BarcodeType.Item.isOfType(barcode) || BarcodeType.Container.isOfType(barcode)) {
                if (mDataManager.getCurrentTransfer() == null) {
                    Toast.makeText(this, "Scan a location first", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mDataManager.getCurrentTransfer().finalized) {
                    Toast.makeText(this, "Start a new transfer by scanning a location first", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mDataManager.isDuplicate(barcode)) {
                    Utils.vibrate(this);
                    Toast.makeText(this, String.format("Duplicate barcode: \"%s\"", barcode), Toast.LENGTH_SHORT).show();
                } else {
                    if (mDataManager.insertItem(barcode) > 0) {
                        this.<AppCompatTextView>findViewById(R.id.text_item_count).setText(String.valueOf(mDataManager.getItemCount()));
                    } else {
                        Log.w(TAG, String.format("Error adding item with barcode of %s to database", barcode));
                        Toast.makeText(TransferActivity.this, "Error adding item", Toast.LENGTH_SHORT).show();
                    }
                }
                AsyncTask.execute(() -> {
                    final int position = mItemRecyclerAdapter.getRowIndexByColumnData(TransferDatabase.Key.BARCODE, barcode);
                    runOnUiThread(() -> {
                        mItemRecyclerAdapter.setSelectedItem(position);
                        this.<RecyclerView>findViewById(R.id.item_recycler_view).scrollToPosition(position);
                    });
                });
            } else {
                Utils.vibrate(this);
                Toast.makeText(this, String.format("Unrecognised barcode: \"%s\"", barcode), Toast.LENGTH_SHORT).show();
                return;
            }

            refreshItemRecyclerAdapter();
        });

        mDataManager.setOnCurrentTransferChangedListener(() -> {
            runOnUiThread(() -> updateInfo(mDataManager.getCurrentTransfer() != null ? mDataManager.getCurrentTransfer().locationBarcode : null, mDataManager.getItemCount(), mDataManager.getTransferId()));
            refreshItemRecyclerAdapter();
            invalidateOptionsMenu();
        });

        try {
            mDataManager.init(this);
        } catch (SQLiteCantOpenDatabaseException e) {
            databaseLoadingError(this::init, this::finish);
        }

        init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //logger.reset(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.activity_transfer, menu);
        //logger.dumpToLog();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_sign:
                if (mDataManager.getCurrentTransfer() == null) {
                    Toast.makeText(this, "Start a transfer first", Toast.LENGTH_SHORT).show();
                } else if (mDataManager.getCurrentTransfer().finalized) {
                    Toast.makeText(this, "Already finalized", Toast.LENGTH_SHORT).show();
                } else if (mDataManager.getCurrentTransfer().canceled) {
                    Toast.makeText(this, "Already canceled", Toast.LENGTH_SHORT).show();
                } else {
                    showSignatureDialog();
                }
                return true;
            case R.id.menu_finalize:
                if (mDataManager.getCurrentTransfer() == null) {
                    Toast.makeText(this, "Start a transfer first", Toast.LENGTH_SHORT).show();
                } else if (mDataManager.getCurrentTransfer().finalized) {
                    Toast.makeText(this, "Already finalized", Toast.LENGTH_SHORT).show();
                } else if (mDataManager.getCurrentTransfer().canceled) {
                    Toast.makeText(this, "Already canceled", Toast.LENGTH_SHORT).show();
                } else {
                    showFinalizeDialog();
                }
                return true;
            case R.id.menu_cancel:
                if (mDataManager.getCurrentTransfer() == null) {
                    Toast.makeText(this, "Start a transfer first", Toast.LENGTH_SHORT).show();
                } else if (mDataManager.getCurrentTransfer().finalized) {
                    Toast.makeText(this, "Already finalized", Toast.LENGTH_SHORT).show();
                } else if (mDataManager.getCurrentTransfer().canceled) {
                    Toast.makeText(this, "Already canceled", Toast.LENGTH_SHORT).show();
                } else {
                    if (mDataManager.isShowingDialog()) {
                        break;
                    } else {
                        mDataManager.setIsShowingDialog(true);
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Cancel transfer")
                            .setMessage("Would you like to cancel this transfer?")
                            .setNegativeButton("no", (dialog, which) -> dialog.dismiss())
                            .setPositiveButton("yes", (dialog, which) -> mDataManager.cancelCurrentTransfer())
                            .setOnDismissListener(dialog -> mDataManager.setIsShowingDialog(false))
                            .create().show();
                }
                return true;
            case R.id.menu_reset:
                if (mDataManager.isShowingDialog()) {
                    break;
                } else {
                    mDataManager.setIsShowingDialog(true);
                }

                new AlertDialog.Builder(this)
                        .setTitle("Reset transfers")
                        .setMessage(
                                "Would you like to clear all transfer history?\n" +
                                "\n" +
                                "Note: This will also delete transfer output and signatures."
                        ).setNegativeButton("no", (dialog, which) -> dialog.dismiss())
                        .setPositiveButton("yes", (dialog, which) -> mDataManager.reset(this))
                        .setOnDismissListener(dialog -> mDataManager.setIsShowingDialog(false))
                        .create().show();
                return true;
            case R.id.menu_continuous_mode:
                if (mDataManager.getScanner() != null && mDataManager.getScanner().setScanMode(item.isChecked() ? AbstractScanner.ONE_SHOT_MODE : AbstractScanner.CONTINUOUS_MODE)) {
                    item.setChecked(!item.isChecked());
                } else {
                    Toast.makeText(this, "An error occurred while changing scanning mode", Toast.LENGTH_SHORT).show();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem itemSign = menu.findItem(R.id.menu_sign);
        final MenuItem itemFinalize = menu.findItem(R.id.menu_finalize);
        final MenuItem itemCancel = menu.findItem(R.id.menu_cancel);
        final MenuItem itemReset = menu.findItem(R.id.menu_reset);
        final MenuItem itemContinuous = menu.findItem(R.id.menu_continuous_mode);
        if (mDataManager != null && mDataManager.getScanner() != null) {
            final boolean editable = mDataManager.getCurrentTransfer() != null && !mDataManager.getCurrentTransfer().finalized && !mDataManager.getCurrentTransfer().canceled;
            itemSign.setVisible(true);
            itemSign.setEnabled(editable);
            itemSign.getIcon().setAlpha(editable ? 255 : 127);
            itemFinalize.setVisible(true);
            itemFinalize.setEnabled(editable);
            itemFinalize.getIcon().setAlpha(editable ? 255 : 127);
            itemCancel.setVisible(true);
            itemCancel.setEnabled(editable);
            itemReset.setVisible(true);
            if (BuildConfig.SUPPORTS_CONTINUOUS && mDataManager.getScanner().getScanMode() != AbstractScanner.UNKNOWN_MODE) {
                itemContinuous.setVisible(true);
                itemContinuous.setChecked(mDataManager.getScanner().getScanMode() == AbstractScanner.CONTINUOUS_MODE);
            } else {
                itemContinuous.setVisible(false);
            }
        } else {
            itemSign.setVisible(false);
            itemFinalize.setVisible(false);
            itemCancel.setVisible(false);
            itemReset.setVisible(false);
            itemContinuous.setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mDataManager.getScanner() != null)
            mDataManager.getScanner().onStart(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mDataManager.getScanner() != null)
            mDataManager.getScanner().onResume(this);
        refreshItemRecyclerAdapter();
    }

    @Override
    protected void onPause() {
        if (mDataManager.getScanner() != null)
            mDataManager.getScanner().onPause(this);
        if (mItemRecyclerAdapter != null && mItemRecyclerAdapter.getCursor() != null)
            mItemRecyclerAdapter.getCursor().close();
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mDataManager.getScanner() != null)
            mDataManager.getScanner().onStop(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mDataManager.isDatabaseOpen())
            mDataManager.closeDatabase();
        if (mDataManager.getScanner() != null)
            mDataManager.getScanner().onDestroy(this);
        super.onDestroy();
    }

    private void databaseLoadingError(final Runnable onDelete, final Runnable onFail) {
        new AlertDialog.Builder(TransferActivity.this)
                .setCancelable(false)
                .setTitle("Database Load Error")
                .setMessage(
                        "There was an error loading the last transfer file, Would you like to delete the it?\n" +
                        "\n" +
                        "Answering no will close the app."
                ).setNegativeButton("no", (dialog, which) -> finish())
                .setPositiveButton("yes", (dialog, which) -> {
                    if (!mDataManager.deleteDatabase(TransferActivity.this) && mDataManager.databaseExists(TransferActivity.this)) {
                        Toast.makeText(TransferActivity.this, "The file could not be deleted", Toast.LENGTH_SHORT).show();
                        onFail.run();
                    } else {
                        Toast.makeText(TransferActivity.this, "The file was deleted", Toast.LENGTH_SHORT).show();
                        onDelete.run();
                    }
                }).setOnDismissListener(dialog -> finish())
                .create().show();
    }

    private void init() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
            return;
        }

        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(String.format("%1s v%2s", getString(R.string.app_name), BuildConfig.VERSION_NAME));

        final RecyclerView itemRecyclerView = findViewById(R.id.item_recycler_view);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mItemRecyclerAdapter = new SelectableCursorRecyclerViewAdapter<TransferItemViewHolder>(mDataManager.getItemListCursor(), TransferDatabase.Key.ID) {
            @Override
            public void onBindViewHolder(TransferItemViewHolder viewHolder, Cursor cursor) {
                viewHolder.bindViews(cursor, getSelectedItem() == viewHolder.getAdapterPosition());
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
        final DividerItemDecoration itemDecoration = new DividerItemDecoration(this, R.drawable.divider_item_transfer, DividerItemDecoration.VERTICAL_LIST);
        itemRecyclerView.addItemDecoration(itemDecoration);

        /*this.<AppCompatButton>findViewById(R.id.test_button).setOnClickListener(v -> {

        });*/
    }

    private void showSignatureDialog() {
        if (mDataManager.isShowingDialog() || mDataManager.getTransferId() < 0) {
            return;
        } else {
            mDataManager.setIsShowingDialog(true);
        }

        final AppCompatDialog compatDialog = new AppCompatDialog(this, R.style.CustomDialogTheme);
        Objects.requireNonNull(compatDialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        compatDialog.setCancelable(false);
        compatDialog.setContentView(R.layout.fragment_sign);
        compatDialog.setOnDismissListener(dialog -> mDataManager.setIsShowingDialog(false));

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
            mDataManager.saveSignature(this, signaturePad.getSignatureBitmap());
            mDataManager.getCurrentTransfer().signed = true;
            compatDialog.dismiss();
        });

        compatDialog.show();
    }

    private void updateInfo(String location, int itemCount, long transferId) {
        this.<AppCompatTextView>findViewById(R.id.text_current_location).setText(getBarcodeType(location).equals(DataManager.BarcodeType.Location) ? location : "-");
        this.<AppCompatTextView>findViewById(R.id.text_item_count).setText(itemCount >= 0 ? String.valueOf(itemCount) : "-");
        this.<AppCompatTextView>findViewById(R.id.text_transfer_id).setText(transferId >= 0 ? String.valueOf(transferId) : "-");
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
    
    private void refreshItemRecyclerAdapter() {
        if (mItemRecyclerAdapter != null) {
            AsyncTask.execute(() -> {
                final Cursor itemListCursor = mDataManager.getItemListCursor();
                runOnUiThread(() -> mItemRecyclerAdapter.changeCursor(itemListCursor));
            });
        }
    }

    private void showFinalizeDialog() {
        if (mDataManager.isShowingDialog()) {
            return;
        } else {
            if (mDataManager.getItemCount() <= 0) {
                Toast.makeText(this, "Scan items first", Toast.LENGTH_SHORT).show();
                return;
            }
            mDataManager.setIsShowingDialog(true);
        }

        new AlertDialog.Builder(this)
                .setTitle("Finalize transfer")
                .setMessage(
                        "Would you like to finalize this transfer?" +
                        (mDataManager.getCurrentTransfer().signed ?
                                "" :
                                "\n" +
                                "\n" +
                                "Note: the current transfer has not been signed"
                        )
                ).setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.action_finalize, (dialog, which) -> mDataManager.saveTransferToFile(this, () -> {
                    this.<MaterialProgressBar>findViewById(R.id.progress_bar).setProgress(0);
                    if (mDataManager.finalizeCurrentTransfer())
                        runOnUiThread(() -> Toast.makeText(this, "Finalized", Toast.LENGTH_SHORT).show());
                }, progress -> {
                    final MaterialProgressBar progressBar = findViewById(R.id.progress_bar);
                    progressBar.setProgress((int) (progress * progressBar.getMax()));
                }, () -> this.<MaterialProgressBar>findViewById(R.id.progress_bar).setProgress(0)))
                .setOnDismissListener(dialog -> mDataManager.setIsShowingDialog(false))
                .create().show();
    }

    private void showRemoveItemDialog(final long id, final String barcode) {
        if (mDataManager.isShowingDialog()) {
            return;
        } else {
            if (mDataManager.getItemCount() <= 0) {
                Toast.makeText(this, "Scan items first", Toast.LENGTH_SHORT).show();
                return;
            }
            mDataManager.setIsShowingDialog(true);
        }

        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle("Remove " + (BarcodeType.Item.isOfType(barcode) ? "Item" : "Container"))
                .setMessage("Are you sure you want to remove " + (BarcodeType.Item.isOfType(barcode) ? "item" : "container") + " \"" + barcode + "\"")
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.action_yes, (dialog, which) -> {
                    if (mDataManager.deleteItem(id) > 0) {
                        TransferActivity.this.<AppCompatTextView>findViewById(R.id.text_item_count).setText(String.valueOf(mDataManager.getItemCount()));
                        refreshItemRecyclerAdapter();
                    } else {
                        Log.w(TAG, String.format("Error deleting item with an id of %d from database", id));
                        Toast.makeText(TransferActivity.this, "Error removing item", Toast.LENGTH_SHORT).show();
                    }
                }).setOnDismissListener(dialog -> mDataManager.setIsShowingDialog(false))
                .create().show();
    }

    private class TransferItemViewHolder extends RecyclerView.ViewHolder {
        private long id;
        private String barcode;

        public TransferItemViewHolder(ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transfer, parent, false));
            final AppCompatImageButton expandedMenuButton = itemView.findViewById(R.id.button_menu);
            expandedMenuButton.setOnClickListener(v -> {
                final PopupMenu popup = new PopupMenu(TransferActivity.this, expandedMenuButton);
                popup.inflate(R.menu.item_transfer);
                popup.getMenu().findItem(R.id.menu_remove).setOnMenuItemClickListener(menuItem -> {
                    showRemoveItemDialog(id, barcode);
                    return true;
                });
                popup.show();
            });
        }

        public void bindViews(final Cursor cursor, final boolean isSelected) {
            id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            barcode = cursor.getString(cursor.getColumnIndexOrThrow("barcode"));
            final AppCompatTextView textBarcode = itemView.findViewById(R.id.text_barcode);
            textBarcode.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            textBarcode.setText(barcode);
        }
    }
}
