package com.porterlee.transfer;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.gcacace.signaturepad.views.SignaturePad;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

import static com.porterlee.transfer.BarcodeType.Item;
import static com.porterlee.transfer.BarcodeType.Container;
import static com.porterlee.transfer.BarcodeType.Location;
import static com.porterlee.transfer.BarcodeType.Invalid;
import static com.porterlee.transfer.BarcodeType.getBarcodeType;

public class TransferActivity extends AppCompatActivity {
    public static final String TAG = TransferActivity.class.getCanonicalName();
    private SelectableCursorRecyclerViewAdapter<TransferItemViewHolder> mItemRecyclerAdapter;
    private DataManager mDataManager;

    private final ScannerUtils.OnBarcodeScannedListener onBarcodeScannedListener = new ScannerUtils.OnBarcodeScannedListener() {
        @Override
        public void onBarcodeScanned(final String barcode) {
            if (mDataManager == null)
                return;

            if (mDataManager.isSaving()) {
                getScannerUtils().onScanComplete(false);
                toastShort("Cannot scan while saving");
                return;
            }

            final BarcodeType barcodeType = getBarcodeType(barcode);
            if (barcodeType.equals(Invalid)) {
                getScannerUtils().onScanComplete(false);
                toastShort(String.format("Unrecognised barcode: \"%s\"", barcode));
                return;
            }

            Utils.Transfer currentTransfer = mDataManager.getCurrentTransfer();
            if (currentTransfer == null || !currentTransfer.isActive()) { // null or inactive current transfer
                if (Item.equals(barcodeType)) {
                    getScannerUtils().onScanComplete(false);
                    if (currentTransfer != null) {
                        if (currentTransfer.canceled) {
                            toastShort("This transfer is canceled");
                            return;
                        } else if (currentTransfer.finalized) {
                            toastShort("This transfer is finalized");
                            return;
                        }
                    }

                    toastShort("Start a new transfer by scanning a location or container");
                    return;
                }

                if (Location.equals(barcodeType) || Container.equals(barcodeType)) {
                    if (mDataManager.startNewTransfer(barcode) > 0) {
                        getScannerUtils().onScanComplete(true);
                        refreshItemRecyclerAdapter();
                    } else {
                        getScannerUtils().onScanComplete(false);
                        Log.w(TAG, String.format("Error adding %s \"%s\" to database", (Location.equals(barcodeType) ? "location" : "container"), barcode));
                        toastLong("Error starting transfer");
                    }
                }
                return;
            }

            // currentTransfer is non-null and active

            if (Container.equals(barcodeType)) {
                // ask if the user would like to start a new transfer or add this to the current one
            }

            if (Location.isOfType(barcode)) {
                getScannerUtils().onScanComplete(false);
                toastShort("Finalize or cancel this transfer first");
            } else if (Item.equals(barcodeType) || Container.equals(barcodeType)) {
                if (mDataManager.query_currentTransferHasItemWithBarcode(barcode)) {
                    getScannerUtils().onScanComplete(false);
                    openDialog_duplicateItemResolution(barcode);
                } else {
                    insertItem(barcode, new Utils.OnFinishListener() {
                        @Override
                        public void onFinish(boolean success) {
                            getScannerUtils().onScanComplete(success);
                        }
                    });
                }
            }
        }
    };

    public void openDialog_duplicateItemResolution(final String barcode) {
        mDataManager.showModalScannerDialog(new AlertDialog.Builder(TransferActivity.this)
                .setCancelable(false)
                .setTitle("Duplicate item")
                .setMessage("An item with the same barcode was already scanned, would you still like to add it to the list?")
                .setNegativeButton(R.string.action_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        highlightItemWithBarcode(barcode);
                    }
                }).setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        insertItem(barcode, null);
                    }
                }).create(), null);
    }

    private void insertItem(String barcode, Utils.OnFinishListener listener) {
        if (mDataManager.query_insertItem(barcode) > 0) {
            if (listener != null)
                listener.onFinish(true);

            refreshItemCount();
            refreshItemRecyclerAdapter();
        } else {
            if (listener != null)
                listener.onFinish(false);

            boolean isItem = Item.isOfType(barcode);
            boolean isContainer = Container.isOfType(barcode);
            String barcodeTypeName = isItem ? "item" : (isContainer ? "container" : "barcode");
            Log.w(TAG, String.format("Error adding %s \"%s\" to database", barcodeTypeName, barcode));
            toastLong("Error adding " + barcodeTypeName);
        }
        highlightItemWithBarcode(barcode);
    }

    private Scanner getScanner() {
        return Scanner.getInstance();
    }

    private ScannerUtils getScannerUtils() {
        return ScannerUtils.getInstance();
    }

    private void refreshItemCount() {
        this.<AppCompatTextView>findViewById(R.id.text_item_count).setText(String.valueOf(mDataManager.query_getItemCount()));
    }

    private void highlightItemWithBarcode(final String barcode) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final int position = mItemRecyclerAdapter.getIndexOfBarcode(barcode);
                mItemRecyclerAdapter.setSelectedItem(position);
                TransferActivity.this.<RecyclerView>findViewById(R.id.item_recycler_view).scrollToPosition(position);
            }
        });
    }
    /*
    public void openDialog_setup() {
        AlertDialog tempAlertDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.text_setup_title)
                .setView(R.layout.fragment_setup)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toastShort("Setup canceled");
                    }
                }).setCancelable(false)
                .create();

        tempAlertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialog) {
                        final AlertDialog alertDialog = (AlertDialog) dialog;
                        final Button buttonSave = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        final AppCompatCheckBox checkBoxRequiresStartupLogin = alertDialog.findViewById(R.id.switch_requires_startup_login);
                        final AppCompatCheckBox checkBoxRequiresAnalystLogin = alertDialog.findViewById(R.id.switch_requires_analyst_login);
                        final AppCompatCheckBox checkBoxRequiresSignature = alertDialog.findViewById(R.id.switch_requires_signature);
                        checkBoxRequiresStartupLogin.setChecked(mDataManager.requiresStartupLogin());
                        checkBoxRequiresStartupLogin.jumpDrawablesToCurrentState();
                        checkBoxRequiresAnalystLogin.setChecked(mDataManager.requiresAnalystLogin());
                        checkBoxRequiresAnalystLogin.jumpDrawablesToCurrentState();
                        checkBoxRequiresSignature.setChecked(mDataManager.requiresSignature());
                        checkBoxRequiresSignature.jumpDrawablesToCurrentState();

                        buttonSave.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                mDataManager.setRequiresStartupLogin(checkBoxRequiresStartupLogin.isChecked());
                                mDataManager.setRequiresAnalystLogin(checkBoxRequiresAnalystLogin.isChecked());
                                mDataManager.setRequiresSignature(checkBoxRequiresSignature.isChecked());
                                alertDialog.dismiss();
                            }
                        });
                    }
                });

        mDataManager.showModalScannerDialog(tempAlertDialog, null);
    }
    */
    public void openDialog_saveTransfer(final Utils.OnFinishListener onFinishListener) {
        mDataManager.showModalScannerDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.text_save_transfer_title)
                .setMessage(R.string.text_save_transfer_body)
                .setPositiveButton(R.string.action_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toastShort("Saving...");
                        final RecyclerView itemRecyclerView = findViewById(R.id.item_recycler_view);
                        for (int i = 0, childCount = itemRecyclerView.getChildCount(); i < childCount; i++) {
                            final View v = itemRecyclerView.getChildAt(i);
                            if (v != null) {
                                final TransferItemViewHolder holder = (TransferItemViewHolder) itemRecyclerView.getChildViewHolder(v);
                                holder.saveQuantity();
                            }
                        }

                        if (!mDataManager.query_updateCurrentTransferSetFinalized())
                            throw new RuntimeException("could not update transfer");
                        mDataManager.saveCurrentBatch(TransferActivity.this, new Utils.OnProgressUpdateListener() {
                            @Override
                            public void onProgressUpdate(float progress) {
                                final MaterialProgressBar progressBar = findViewById(R.id.progress_bar);
                                progressBar.setProgress((int) (progress * progressBar.getMax()));
                            }
                        }, new Utils.DetailedOnFinishListener() {
                            @Override
                            public void onFinish(boolean success, String message) {
                                TransferActivity.this.<MaterialProgressBar>findViewById(R.id.progress_bar).setProgress(0);
                                if (success) {
                                    toastShort(message);
                                } else {
                                    toastLong(message);
                                }

                                if (onFinishListener != null)
                                    onFinishListener.onFinish(success);
                            }
                        });
                    }
                }).setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (onFinishListener != null)
                            onFinishListener.onFinish(false);
                    }
                }).setCancelable(false)
                .create(), null);
    }

    private void openDialog_signTransfer(final Utils.OnFinishListener onFinishListener) {
        AlertDialog tempAlertDialog = new AlertDialog.Builder(this)
                .setView(R.layout.fragment_sign)
                .setPositiveButton(R.string.action_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toastShort("Saving...");
                        mDataManager.signCurrentTransfer(
                                TransferActivity.this,
                                ((AlertDialog) dialog).<SignaturePad>findViewById(R.id.signature_pad).getSignatureBitmap(),
                                new Utils.DetailedOnFinishListener() {
                                    @Override
                                    public void onFinish(boolean success, String message) {
                                        if (success) {
                                            toastShort(message);
                                        } else {
                                            toastLong(message);
                                        }
                                        if (onFinishListener != null)
                                            onFinishListener.onFinish(success);
                                    }
                                }
                        );
                    }
                }).setNeutralButton(R.string.action_clear, null)
                .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (onFinishListener != null)
                            onFinishListener.onFinish(false);
                    }
                }).setCancelable(false)
                .create();

        tempAlertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                final AlertDialog alertDialog = (AlertDialog) dialog;
                final SignaturePad signaturePad = alertDialog.findViewById(R.id.signature_pad);
                final Button buttonClear = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
                final Button buttonSave = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);

                buttonClear.setEnabled(false);
                buttonSave.setEnabled(false);
                buttonClear.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        signaturePad.clear();
                    }
                });
                signaturePad.setOnSignedListener(new SignaturePad.OnSignedListener() {
                    @Override
                    public void onStartSigning() {
                    }

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
            }
        });

        mDataManager.showModalScannerDialog(tempAlertDialog, null);
    }

    private void openDialog_cancelTransfer() {
        mDataManager.showModalScannerDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.text_cancel_transfer_title)
                .setMessage(R.string.text_cancel_transfer_body)
                .setNegativeButton(R.string.action_no, null)
                .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mDataManager.query_updateCurrentTransferSetCanceled()) {
                            toastShort("Transfer canceled");
                        } else {
                            toastLong("There was an error canceling");
                        }
                    }
                }).create(), null);
    }
/*
    private void openDialog_resetDatabase() {
        mDataManager.showModalScannerDialog(new AlertDialog.Builder(this)
                .setTitle("Reset transfers")
                .setMessage("Would you like to clear all transfer output and signatures?")
                .setNegativeButton(R.string.action_no, null)
                .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mDataManager.reset(getApplicationContext());
                    }
                }).create(), null);
    }
*/

    private void setDrawableEnabled(final Drawable drawable, boolean enabled) {
        if (drawable != null)
            drawable.setAlpha(enabled ? 255 : 127);
    }

    private void setImageButtonEnabled(final AppCompatImageButton imageButton, boolean enabled) {
        imageButton.setEnabled(enabled);
        setDrawableEnabled(imageButton.getDrawable(), enabled);
    }

    public void switchToPreviousTransfer(View v) {
        mDataManager.switchToPreviousTransfer();
    }

    public void switchToNextTransfer(View v) {
        mDataManager.switchToPreviousTransfer();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDataManager = new DataManager(getPreferences(Context.MODE_PRIVATE));

        getScannerUtils().setActivity(this);

        try {
            getScanner().init();
        } catch (RuntimeException e) {
            e.printStackTrace();
            finish();
            toastShort("Scanner failed to initialize");
            return;
        }

        setContentView(R.layout.activity_transfer);

        if (BuildConfig.displayTransferNavigation) {
            this.<AppCompatImageButton>findViewById(R.id.button_left).setVisibility(View.VISIBLE);
            this.<AppCompatImageButton>findViewById(R.id.button_right).setVisibility(View.VISIBLE);
        }

        mItemRecyclerAdapter = new SelectableCursorRecyclerViewAdapter<TransferItemViewHolder>(mDataManager.query_getItems(), TransferDatabase.Key.ID) {
            @Override
            public void onBindViewHolder(TransferItemViewHolder viewHolder, Cursor cursor) {
                viewHolder.bindViews(cursor, getSelectedItem() == viewHolder.getAdapterPosition(), getIsCanceled());
            }

            @NonNull
            @Override
            public TransferItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new TransferItemViewHolder(parent);
            }

            @Override
            public void onViewRecycled(@NonNull TransferItemViewHolder holder) {
                holder.saveQuantity();
            }
        };

        mDataManager.setOnCurrentTransferChangedListener(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Utils.Transfer currentTransfer = mDataManager.getCurrentTransfer();
                        long transferId = mDataManager.getCurrentTransferId();
                        boolean isCanceled = currentTransfer != null && mDataManager.getCurrentTransfer().canceled;
                        updateInfo(
                                currentTransfer != null ? currentTransfer.location_barcode : null,
                                transferId < 0 ? -1 : mDataManager.query_getItemCount(),
                                transferId,
                                isCanceled
                        );

                        boolean hasActiveTransfer = mDataManager.query_hasActiveTransfer();
                        {
                            final AppCompatImageButton buttonLeft = findViewById(R.id.button_left);
                            setImageButtonEnabled(buttonLeft, !hasActiveTransfer && mDataManager.query_hasPreviousTransfer());
                        }
                        {
                            final AppCompatImageButton buttonRight = findViewById(R.id.button_right);
                            setImageButtonEnabled(buttonRight, !hasActiveTransfer && mDataManager.query_hasNextTransfer());
                        }

                        refreshItemRecyclerAdapter();
                        mItemRecyclerAdapter.setIsCanceled(isCanceled);

                        invalidateOptionsMenu();
                    }
                });
            }
        });

        try {
            mDataManager.init(getApplicationContext());
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
        getMenuInflater().inflate(R.menu.activity_transfer, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_save:
                if (mDataManager.getCurrentTransfer() == null) {
                    toastShort("Start a transfer first");
                } else if (mDataManager.getCurrentTransfer().finalized) {
                    toastShort("Already finalized");
                } else if (mDataManager.getCurrentTransfer().canceled) {
                    toastShort("Already canceled");
                } else if (mDataManager.query_getItemCount() <= 0) {
                    toastShort("Scan items first");
                } else if (mDataManager.isSaving()) {
                    toastShort("Cannot finalize while saving");
                } else {
                    openDialog_saveTransfer(null);
                }
                return true;
            case R.id.menu_sign:
                if (mDataManager.getCurrentTransfer() == null) {
                    toastShort("Start a transfer first");
                } else if (mDataManager.getCurrentTransfer().signed) {
                    toastShort("Already signed");
                } else if (mDataManager.getCurrentTransfer().finalized) {
                    toastShort("Already finalized");
                } else if (mDataManager.getCurrentTransfer().canceled) {
                    toastShort("Already canceled");
                } else if (mDataManager.query_getItemCount() <= 0) {
                    toastShort("Scan items first");
                } else if (mDataManager.isSaving()) {
                    toastShort("Cannot finalize while saving");
                } else {
                    openDialog_signTransfer(null);
                }
                return true;
            case R.id.menu_cancel:
                if (mDataManager.getCurrentTransfer() == null) {
                    toastShort("Start a transfer first");
                } else if (mDataManager.getCurrentTransfer().canceled) {
                    toastShort("Already canceled");
                } else if (mDataManager.isSaving()) {
                    toastShort("Cannot cancel while saving");
                } else {
                    openDialog_cancelTransfer();
                }
                return true;/*
            case R.id.menu_reset:
                if (mDataManager.isSaving()) {
                    toastShort("Cannot reset while saving");
                } else {
                    openDialog_resetDatabase();
                }
                return true;
            case R.id.menu_open_setup_dialog:
                if (mDataManager.isSaving()) {
                    toastShort("Cannot load analysts while saving");
                } else {
                    openDialog_setup();
                }
                return true;*/
            /*case R.id.menu_continuous_mode:
                if (mDataManager.getScanner() != null && mDataManager.getScanner().setScanMode(item.isChecked() ? AbstractScanner.ONE_SHOT_MODE : AbstractScanner.CONTINUOUS_MODE)) {
                    item.setChecked(!item.isChecked());
                } else {
                    toastShort("An error occurred while changing scanning mode");
                }
                return true;*/
        }
        return super.onOptionsItemSelected(item);
    }

    private void setIconEnabled(final MenuItem item, boolean enabled) {
        item.setEnabled(enabled);
        setDrawableEnabled(item.getIcon(), enabled);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem itemSave = menu.findItem(R.id.menu_save);
        final MenuItem itemSign = menu.findItem(R.id.menu_sign);
        final MenuItem itemCancel = menu.findItem(R.id.menu_cancel);
        //final MenuItem itemReset = menu.findItem(R.id.menu_reset);
        //final MenuItem itemContinuous = menu.findItem(R.id.menu_continuous_mode);
        if (mDataManager != null) {
            final boolean saveEnabled = mDataManager.getCurrentTransfer() != null;
            final boolean signEnabled = mDataManager.getCurrentTransfer() != null && !mDataManager.getCurrentTransfer().signed && !mDataManager.getCurrentTransfer().finalized && !mDataManager.getCurrentTransfer().canceled;
            final boolean cancelEnabled = mDataManager.getCurrentTransfer() != null && !mDataManager.getCurrentTransfer().canceled;

            itemSave.setVisible(true);
            itemSign.setVisible(true);
            itemCancel.setVisible(true);
            //itemReset.setVisible(true);

            setIconEnabled(itemSave, saveEnabled);
            setIconEnabled(itemSign, signEnabled);
            setIconEnabled(itemCancel, cancelEnabled);
            //setIconEnabled(itemReset, resetEnabled);
        } else {
            itemSave.setVisible(false);
            itemSign.setVisible(false);
            itemCancel.setVisible(false);
            //itemReset.setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getScannerUtils().setActivity(this);
        getScannerUtils().setOnBarcodeScannedListener(onBarcodeScannedListener);
        getScanner().start();
        refreshItemRecyclerAdapter();
    }

    @Override
    protected void onPause() {
        getScanner().stop();
        if (mItemRecyclerAdapter != null && mItemRecyclerAdapter.getCursor() != null)
            mItemRecyclerAdapter.swapCursor(null).close();
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mDataManager.closeDatabase();
        getScanner().release();
        super.onDestroy();
    }

    private void databaseLoadingError(final Runnable onDelete, final Runnable onFail) {
        mDataManager.showModalScannerDialog(new AlertDialog.Builder(TransferActivity.this)
                .setCancelable(false)
                .setTitle("Database Load Error")
                .setMessage(
                        "There was an error loading the last transfer file, Would you like to delete the it?\n" +
                        "\n" +
                        "Answering no will close the app."
                ).setNegativeButton(R.string.action_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                }).setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!mDataManager.deleteDatabase(getApplicationContext()) && mDataManager.databaseExists(getApplicationContext())) {
                            toastLong("The file could not be deleted");
                            onFail.run();
                        } else {
                            toastShort("The file was deleted");
                            onDelete.run();
                        }
                    }
                }).create(), new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });
    }

    private void init() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
            return;
        }

        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(String.format("%s v%s", getString(R.string.app_name), BuildConfig.VERSION_NAME));

        final RecyclerView itemRecyclerView = findViewById(R.id.item_recycler_view);
        itemRecyclerView.setLayoutManager(new LinearLayoutManager(this));
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
        if (BuildConfig.displayQuantity) {
            final SoftKeyboardHandledConstraintLayout softKeyboardHandler = findViewById(R.id.transfer_layout);
            softKeyboardHandler.setOnSoftKeyboardVisibilityChangeListener(new SoftKeyboardHandledConstraintLayout.SoftKeyboardVisibilityChangeListener() {
                @Override
                public void onSoftKeyboardShow() {
                }

                @Override
                public void onSoftKeyboardHide() {
                    final View view = getCurrentFocus();
                    if (view != null)
                        view.clearFocus();
                }
            });
        }

        if (mDataManager.getLastVersion().compareTo(new Version(2, 0, 0)) < 0) {
            Log.v(TAG, "updating old transfers to new batch id");
            mDataManager.updateOldInProgressTransfersBatchIds();
        }

        if (mDataManager.getLastVersion().compareTo(mDataManager.getCurrentVersion()) < 0) {
            mDataManager.preferences_setVersion(mDataManager.getCurrentVersion());
        }
    }

    private void toastShort(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toastLong(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toastShort(@StringRes final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toastLong(@StringRes final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateInfo(String location, int itemCount, long transferId, boolean isCanceled) {
        final boolean isLocation = getBarcodeType(location).equals(BarcodeType.Location);
        final boolean isContainer = getBarcodeType(location).equals(BarcodeType.Container);
        final boolean isLocationNull = !isLocation && !isContainer;

        final AppCompatTextView textScanBarcodeHint = findViewById(R.id.text_scan_barcode_hint);

        final AppCompatTextView textCurrentLocationLabel = findViewById(R.id.text_current_location_label);
        final AppCompatTextView textCurrentLocation = findViewById(R.id.text_current_location);

        final AppCompatTextView textItemCountLabel = findViewById(R.id.text_item_count_label);
        final AppCompatTextView textItemCount = findViewById(R.id.text_item_count);

        final AppCompatTextView textTransferIdLabel = findViewById(R.id.text_transfer_id_label);
        final AppCompatTextView textTransferId = findViewById(R.id.text_transfer_id);

        {
            textScanBarcodeHint.setVisibility(isLocationNull ? View.VISIBLE : View.INVISIBLE);

            textCurrentLocationLabel.setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);
            textCurrentLocation.setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);

            textItemCountLabel.setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);
            textItemCount.setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);

            textTransferIdLabel.setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);
            textTransferId.setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);
        }

        {
            textCurrentLocationLabel.setEnabled(!isCanceled);
            textCurrentLocation.setEnabled(!isCanceled);

            textItemCountLabel.setEnabled(!isCanceled);
            textItemCount.setEnabled(!isCanceled);

            textTransferIdLabel.setEnabled(!isCanceled);
            textTransferId.setEnabled(!isCanceled);
        }

        if (!isLocationNull) {
            textCurrentLocationLabel.setText(isLocation ? R.string.text_location_label : R.string.text_container_location_label);
            textCurrentLocation.setText(location);
            textItemCount.setText(itemCount >= 0 ? String.valueOf(itemCount) : "-");
            if (transferId < 0) {
                textTransferIdLabel.setVisibility(View.INVISIBLE);
                textTransferId.setVisibility(View.VISIBLE);
                textTransferId.setText("-");
            } else {
                textTransferIdLabel.setVisibility(View.VISIBLE);
                textTransferId.setVisibility(View.VISIBLE);
                textTransferId.setText(String.valueOf(transferId));
            }
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

    private void refreshItemRecyclerAdapter() {
        refreshItemRecyclerAdapter(mDataManager.query_getItems());
    }

    private void refreshItemRecyclerAdapter(final Cursor itemListCursor) {
        if (mItemRecyclerAdapter != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mItemRecyclerAdapter.changeCursor(itemListCursor);
                }
            });
        }
    }

    private class TransferItemViewHolder extends RecyclerView.ViewHolder {
        private long id;
        private String barcode;
        private int quantity;

        public TransferItemViewHolder(ViewGroup parent) {
            super(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transfer, parent, false));
            final AppCompatImageButton expandedMenuButton = itemView.findViewById(R.id.button_menu);
            expandedMenuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final PopupMenu popup = new PopupMenu(TransferActivity.this, expandedMenuButton);
                    popup.inflate(R.menu.item_transfer);
                    popup.getMenu().findItem(R.id.menu_remove).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            mDataManager.showModalScannerDialog(new AlertDialog.Builder(TransferActivity.this)
                                    .setCancelable(false)
                                    .setTitle("Remove " + BarcodeType.getBarcodeType(barcode).name())
                                    .setMessage("Are you sure you want to remove " + (BarcodeType.Item.isOfType(barcode) ? "item" : "container") + " \"" + barcode + "\"")
                                    .setNegativeButton(R.string.action_cancel, null)
                                    .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (mDataManager.query_deleteItem(id) > 0) {
                                                TransferActivity.this.<AppCompatTextView>findViewById(R.id.text_item_count).setText(String.valueOf(mDataManager.query_getItemCount()));
                                                refreshItemRecyclerAdapter();
                                            } else {
                                                Log.w(TAG, String.format("Error deleting item with an id of %d from database", id));
                                                toastShort("Error removing item");
                                            }
                                        }
                                    }).create(), null);
                            return true;
                        }
                    });
                    popup.show();
                }
            });
            if (BuildConfig.displayQuantity) {
                final AppCompatEditText quantityEditText = itemView.findViewById(R.id.edit_quantity);
                quantityEditText.setVisibility(View.VISIBLE);
                quantityEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (!hasFocus) {
                            saveQuantity();
                        }
                    }
                });
                quantityEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        if (actionId== EditorInfo.IME_ACTION_DONE) {
                            saveQuantity();
                            quantityEditText.clearFocus();
                            InputMethodManager imm =(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(quantityEditText.getWindowToken(), 0);
                            return true;
                        }
                        return false;
                    }
                });
                expandedMenuButton.setFocusable(false);
            }
        }

        void saveQuantity() {
            if (BuildConfig.displayQuantity) {
                final AppCompatEditText quantityEditText = itemView.findViewById(R.id.edit_quantity);
                try {
                    String quantityText = quantityEditText.getText().toString();
                    if(quantityText.length() > 0) {
                        int inputQuantity = Integer.parseInt(quantityText);
                        if (mDataManager.query_updateItemQuantity(id, inputQuantity)) {
                            quantity = inputQuantity;
                        } else {
                            quantityEditText.setText(String.valueOf(quantity));
                            toastLong("Could not set quantity");
                        }
                    } else {
                        quantityEditText.setText(String.valueOf(quantity));
                    }
                } catch(NumberFormatException e) {
                    quantityEditText.setText(String.valueOf(quantity));
                    toastLong("Quantity incorrectly formatted");
                }
            }
        }

        public void bindViews(final Cursor cursor, final boolean isSelected, final boolean isCanceled) {
            id = cursor.getLong(cursor.getColumnIndexOrThrow(TransferDatabase.Key.ID));
            barcode = cursor.getString(cursor.getColumnIndexOrThrow(TransferDatabase.Key.BARCODE));
            quantity = (int) cursor.getLong(cursor.getColumnIndexOrThrow(TransferDatabase.Key.QUANTITY));

            final AppCompatTextView textBarcode = itemView.findViewById(R.id.text_barcode);
            final AppCompatImageButton buttonMenu = itemView.findViewById(R.id.button_menu);

            textBarcode.setEnabled(!isCanceled);
            buttonMenu.setVisibility(isCanceled ? View.GONE : View.VISIBLE);

            textBarcode.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            textBarcode.setText(barcode);

            if (mItemRecyclerAdapter.getIsDuplicate(barcode)) {
                itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.item_color_yellow, null));
            } else {
                itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.item_color_white, null));
            }

            if (BuildConfig.displayQuantity) {
                final AppCompatEditText quantityEditText = itemView.findViewById(R.id.edit_quantity);
                quantityEditText.setEnabled(!isCanceled);
                quantityEditText.setText(cursor.getString(cursor.getColumnIndexOrThrow(TransferDatabase.Key.QUANTITY)));
                if (isSelected) {
                    quantityEditText.requestFocus();
                } else {
                    quantityEditText.clearFocus();
                }
            }
        }
    }
}
