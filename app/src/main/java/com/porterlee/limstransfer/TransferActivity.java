package com.porterlee.limstransfer;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.porterlee.plcscanners.AbstractScanner;

import me.zhanghai.android.materialprogressbar.MaterialProgressBar;

import static com.porterlee.limstransfer.BarcodeType.Item;
import static com.porterlee.limstransfer.BarcodeType.Container;
import static com.porterlee.limstransfer.BarcodeType.Location;
import static com.porterlee.limstransfer.BarcodeType.Invalid;
import static com.porterlee.limstransfer.BarcodeType.getBarcodeType;

public class TransferActivity extends AppCompatActivity {
    public static final String TAG = TransferActivity.class.getCanonicalName();
    private SelectableCursorRecyclerViewAdapter<TransferItemViewHolder> mItemRecyclerAdapter;
    private DataManager mDataManager;

    private final AbstractScanner.OnBarcodeScannedListener onBarcodeScannedListener = new AbstractScanner.OnBarcodeScannedListener() {
        @Override
        public void onBarcodeScanned(final String barcode) {
            if (mDataManager == null)
                return;

            if (mDataManager.isSaving()) {
                AbstractScanner.onScanComplete(false);
                toastShort("Cannot scan while saving");
            } else if (getBarcodeType(barcode).equals(Invalid)) {
                AbstractScanner.onScanComplete(false);
                toastLong(String.format("Unrecognised barcode: \"%s\"", barcode));
            } else if (mDataManager.hasOngoingTransfer()) {
                final boolean isItem;
                if (Location.isOfType(barcode)) {
                    AbstractScanner.onScanComplete(false);
                    toastShort("Finalize or cancel this transfer first");
                } else if ((isItem = Item.isOfType(barcode)) || Container.isOfType(barcode)) {
                    if (mDataManager.isDuplicate(barcode)) {
                        AbstractScanner.onScanComplete(false);
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
                                        if (mDataManager.insertItem(barcode) > 0) {
                                            refreshItemCount();
                                            refreshItemRecyclerAdapter();
                                        } else {
                                            Log.w(TAG, String.format("Error adding %s \"%s\" to database", isItem ? "item" : "container", barcode));
                                            toastLong("Error adding " + (isItem ? "item" : "container"));
                                        }
                                        highlightItemWithBarcode(barcode);
                                    }
                                }).create(), null);
                    } else {
                        if (mDataManager.insertItem(barcode) > 0) {
                            AbstractScanner.onScanComplete(true);
                            refreshItemCount();
                            refreshItemRecyclerAdapter();
                        } else {
                            AbstractScanner.onScanComplete(false);
                            Log.w(TAG, String.format("Error adding %s \"%s\" to database", isItem ? "item" : "container", barcode));
                            toastLong("Error adding " + (isItem ? "item" : "container"));
                        }
                        highlightItemWithBarcode(barcode);
                    }
                }
            } else {
                final boolean isLocation;
                if (Item.isOfType(barcode)) {
                    AbstractScanner.onScanComplete(false);
                    toastShort("Start a new transfer by scanning a location or container");
                } else if ((isLocation = Location.isOfType(barcode)) || Container.isOfType(barcode)) {
                    if (mDataManager.newTransfer(barcode) > 0) {
                        AbstractScanner.onScanComplete(true);
                        refreshItemRecyclerAdapter();
                    } else {
                        AbstractScanner.onScanComplete(false);
                        Log.w(TAG, String.format("Error adding %s \"%s\" to database", (isLocation ? "location" : "container"), barcode));
                        toastLong("Error starting transfer");
                    }
                }
            }
        }
    };

    private AbstractScanner getScanner() {
        return AbstractScanner.getInstance();
    }

    private void refreshItemCount() {
        this.<AppCompatTextView>findViewById(R.id.text_item_count).setText(String.valueOf(mDataManager.getItemCount()));
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

    public void openSetupDialog() {
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

    public void openFinalizeDialog(final Utils.OnFinishListener onFinishListener) {
        mDataManager.showModalScannerDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.text_finalize_transfer_title)
                .setMessage(R.string.text_finalize_transfer_body)
                .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toastShort("Finalize canceled");
                        if (onFinishListener != null)
                            onFinishListener.onFinish(false);
                    }
                }).setPositiveButton(R.string.action_finalize, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toastShort("Finalizing...");
                        mDataManager.finalizeCurrentTransfer(TransferActivity.this, new Utils.OnProgressUpdateListener() {
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
                }).setCancelable(false)
                .create(), null);
    }

    private void openSignDialog(final Utils.OnFinishListener onFinishListener) {
        AlertDialog tempAlertDialog = new AlertDialog.Builder(this)
                .setView(R.layout.fragment_sign)
                .setPositiveButton(R.string.action_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mDataManager.signCurrentTransfer(TransferActivity.this, ((AlertDialog) dialog).<SignaturePad>findViewById(R.id.signature_pad).getSignatureBitmap(), mDataManager.getCurrentTransfer(), new Utils.DetailedOnFinishListener() {
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
                        });
                    }
                }).setNeutralButton(R.string.action_clear, null)
                .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toastShort("Signing canceled");
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
                alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
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

    public void openAnalystLoginDialog(final Utils.OnFinishListener onFinishListener) {
        final AlertDialog analystLoginDialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.text_analyst_login_title)
                .setView(R.layout.fragment_login)
                .setPositiveButton(R.string.action_login, null)
                .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toastShort("Login canceled");
                        if (onFinishListener != null)
                            onFinishListener.onFinish(false);
                    }
                }).create();

        analystLoginDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                final AlertDialog alertDialog = (AlertDialog) dialog;
                final Button buttonLogin = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                final AppCompatTextView textAnalystDescription = alertDialog.findViewById(R.id.text_analyst_description);
                textAnalystDescription.setText(mDataManager.getCurrentAnalyst().getDescription());
                final AppCompatEditText editAnalystPassword = alertDialog.findViewById(R.id.edit_analyst_password);

                alertDialog.findViewById(R.id.edit_analyst_id).setVisibility(View.INVISIBLE);

                buttonLogin.setEnabled(false);
                buttonLogin.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DataManager.Analyst analyst = mDataManager.getCurrentAnalyst();
                        if (analyst == null) {
                            toastShort("Analyst not found");
                        } else {
                            if (analyst.verifyAnalystLogin(editAnalystPassword.getText().toString())) {
                                toastShort("Login successful");
                                alertDialog.dismiss();
                                if (onFinishListener != null)
                                    onFinishListener.onFinish(true);
                            } else {
                                toastShort("Incorrect password");
                            }
                        }
                    }
                });
                editAnalystPassword.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) { }

                    @Override
                    public void afterTextChanged(Editable s) {
                        buttonLogin.setEnabled(!s.toString().equals(""));
                    }
                });
                editAnalystPassword.requestFocus();
            }
        });
        mDataManager.showScannerDialog(analystLoginDialog, null, new AbstractScanner.OnBarcodeScannedListener() {
            @Override
            public void onBarcodeScanned(String s) {
                analystLoginDialog.<AppCompatEditText>findViewById(R.id.edit_analyst_id).setText(s);
            }
        });
    }

    private void openCancelDialog() {
        mDataManager.showModalScannerDialog(new AlertDialog.Builder(this)
                .setTitle(R.string.text_cancel_transfer_title)
                .setMessage(R.string.text_cancel_transfer_body)
                .setNegativeButton(R.string.action_no, null)
                .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (mDataManager.cancelCurrentTransfer()) {
                            toastShort("Transfer canceled");
                        } else {
                            toastLong("There was an error canceling");
                        }
                    }
                }).create(), null);
    }

    private void openResetDialog() {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!AbstractScanner.isCompatible()) {
            Log.w(TAG, "Might not be compatible");
        }

        mDataManager = new DataManager(this);

        if (!mDataManager.initScanner()) {
            finish();
            toastShort("Scanner failed to initialize");
            return;
        }

        mDataManager.pushOnBarcodeScannedListener(onBarcodeScannedListener);
        mDataManager.pushOnBarcodeScannedListener(null);

        setContentView(R.layout.activity_transfer);

        mDataManager.setOnCurrentTransferChangedListener(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateInfo(mDataManager.getCurrentTransfer() != null ? mDataManager.getCurrentTransfer().getLocationBarcode() : null, mDataManager.getItemCount(), mDataManager.getCurrentTransferId());
                    }
                });
                refreshItemRecyclerAdapter();
                invalidateOptionsMenu();
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
        return super.onCreateOptionsMenu(menu) | getScanner().onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_finalize:
                if (mDataManager.getCurrentTransfer() == null) {
                    toastShort("Start a transfer first");
                } else if (mDataManager.getCurrentTransfer().isFinalized()) {
                    toastShort("Already finalized");
                } else if (mDataManager.getCurrentTransfer().isCanceled()) {
                    toastShort("Already canceled");
                } else if (mDataManager.getItemCount() <= 0) {
                    toastShort("Scan items first");
                } else if (mDataManager.isSaving()) {
                    toastShort("Cannot finalize while saving");
                } else {
                    openFinalizeDialog(null);
                }
                return true;
            case R.id.menu_sign:
                if (mDataManager.getCurrentTransfer() == null) {
                    toastShort("Start a transfer first");
                } else if (mDataManager.getCurrentTransfer().isFinalized()) {
                    toastShort("Already finalized");
                } else if (mDataManager.getCurrentTransfer().isCanceled()) {
                    toastShort("Already canceled");
                } else if (mDataManager.getItemCount() <= 0) {
                    toastShort("Scan items first");
                } else if (mDataManager.isSaving()) {
                    toastShort("Cannot finalize while saving");
                } else {
                    openSignDialog(null);
                }
                return true;
            case R.id.menu_cancel:
                if (mDataManager.getCurrentTransfer() == null) {
                    toastShort("Start a transfer first");
                } else if (mDataManager.getCurrentTransfer().isFinalized()) {
                    toastShort("Already finalized");
                } else if (mDataManager.getCurrentTransfer().isCanceled()) {
                    toastShort("Already canceled");
                } else if (mDataManager.isSaving()) {
                    toastShort("Cannot cancel while saving");
                } else {
                    openCancelDialog();
                }
                return true;
            case R.id.menu_reset:
                if (mDataManager.isSaving()) {
                    toastShort("Cannot reset while saving");
                } else {
                    openResetDialog();
                }
                return true;/*
            case R.id.menu_load_analysts:
                if (mDataManager.isSaving()) {
                    toastShort("Cannot load analysts while saving");
                } else {
                    mDataManager.loadAnalysts();
                }
                return true;
            case R.id.menu_open_setup_dialog:
                if (mDataManager.isSaving()) {
                    toastShort("Cannot load analysts while saving");
                } else {
                    openSetupDialog();
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
        return super.onOptionsItemSelected(item) | getScanner().onOptionsItemSelected(item) ;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem itemFinalize = menu.findItem(R.id.menu_finalize);
        final MenuItem itemCancel = menu.findItem(R.id.menu_cancel);
        final MenuItem itemReset = menu.findItem(R.id.menu_reset);
        //final MenuItem itemContinuous = menu.findItem(R.id.menu_continuous_mode);
        if (mDataManager != null) {
            final boolean editable = mDataManager.getCurrentTransfer() != null && !mDataManager.getCurrentTransfer().isFinalized() && !mDataManager.getCurrentTransfer().isCanceled();
            itemFinalize.setVisible(true);
            itemFinalize.setEnabled(editable);
            itemFinalize.getIcon().setAlpha(editable ? 255 : 127);
            itemCancel.setVisible(true);
            itemCancel.setEnabled(editable);
            itemReset.setVisible(true);
            /*if (BuildConfig.SUPPORTS_CONTINUOUS && mDataManager.getScanner().getScanMode() != AbstractScanner.UNKNOWN_MODE) {
                itemContinuous.setVisible(true);
                itemContinuous.setChecked(mDataManager.getScanner().getScanMode() == AbstractScanner.CONTINUOUS_MODE);
            } else {
                itemContinuous.setVisible(false);
            }*/
        } else {
            itemFinalize.setVisible(false);
            itemCancel.setVisible(false);
            itemReset.setVisible(false);
            //itemContinuous.setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu) | getScanner().onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        super.onStart();
        getScanner().onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getScanner().onResume();
        mDataManager.popOnBarcodeScannedListener();
        AbstractScanner.setActivity(this);
        refreshItemRecyclerAdapter();
    }

    @Override
    protected void onPause() {
        AbstractScanner.setActivity(null);
        mDataManager.pushOnBarcodeScannedListener(null);
        getScanner().onPause();
        if (mItemRecyclerAdapter != null && mItemRecyclerAdapter.getCursor() != null)
            mItemRecyclerAdapter.swapCursor(null).close();
        super.onPause();
    }

    @Override
    protected void onStop() {
        getScanner().onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mDataManager.isDatabaseOpen())
            mDataManager.closeDatabase();
        getScanner().onDestroy();
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
        final SoftKeyboardHandledConstraintLayout softKeyboardHandler = findViewById(R.id.transfer_layout);
        softKeyboardHandler.setOnSoftKeyboardVisibilityChangeListener(new SoftKeyboardHandledConstraintLayout.SoftKeyboardVisibilityChangeListener() {
            @Override
            public void onSoftKeyboardShow() { }

            @Override
            public void onSoftKeyboardHide() {
                final View view = getCurrentFocus();
                if (view  != null)
                    view.clearFocus();
            }
        });
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

    private void updateInfo(String location, int itemCount, long transferId) {
        final boolean isLocation = getBarcodeType(location).equals(BarcodeType.Location);
        final boolean isContainer = getBarcodeType(location).equals(BarcodeType.Container);
        final boolean isLocationNull = !isLocation && !isContainer;
        this.<AppCompatTextView>findViewById(R.id.text_scan_barcode).setVisibility(isLocationNull ? View.VISIBLE : View.INVISIBLE);
        this.<AppCompatTextView>findViewById(R.id.text_current_location_label).setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);
        this.<AppCompatTextView>findViewById(R.id.text_current_location).setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);
        this.<AppCompatTextView>findViewById(R.id.text_item_count_label).setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);
        this.<AppCompatTextView>findViewById(R.id.text_item_count).setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);
        this.<AppCompatTextView>findViewById(R.id.text_transfer_id_label).setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);
        this.<AppCompatTextView>findViewById(R.id.text_transfer_id).setVisibility(isLocationNull ? View.INVISIBLE : View.VISIBLE);
        if (!isLocationNull) {
            this.<AppCompatTextView>findViewById(R.id.text_current_location_label).setText(isLocation ? R.string.text_location_label : R.string.text_container_location_label);
            this.<AppCompatTextView>findViewById(R.id.text_current_location).setText(location);
            this.<AppCompatTextView>findViewById(R.id.text_item_count).setText(itemCount >= 0 ? String.valueOf(itemCount) : "-");
            if (transferId < 0) {
                this.<AppCompatTextView>findViewById(R.id.text_transfer_id_label).setVisibility(View.INVISIBLE);
                this.<AppCompatTextView>findViewById(R.id.text_transfer_id).setVisibility(View.INVISIBLE);
            } else {
                this.<AppCompatTextView>findViewById(R.id.text_transfer_id_label).setVisibility(View.VISIBLE);
                this.<AppCompatTextView>findViewById(R.id.text_transfer_id).setVisibility(View.VISIBLE);
                this.<AppCompatTextView>findViewById(R.id.text_transfer_id).setText(String.valueOf(transferId));
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
        if (mItemRecyclerAdapter != null) {
            final Cursor itemListCursor = mDataManager.getItemListCursor();
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
            super(LayoutInflater.from(parent.getContext()).inflate(BuildConfig.display_quantity ? R.layout.item_quantity_transfer : R.layout.item_transfer, parent, false));
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
                                            if (mDataManager.deleteItem(id) > 0) {
                                                TransferActivity.this.<AppCompatTextView>findViewById(R.id.text_item_count).setText(String.valueOf(mDataManager.getItemCount()));
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
            if (BuildConfig.display_quantity) {
                final AppCompatEditText quantityEditText = itemView.findViewById(R.id.edit_quantity);
                final Runnable setQuantity = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String quantityText = quantityEditText.getText().toString();
                            if(quantityText.length() > 0) {
                                int inputQuantity = Integer.parseInt(quantityText);
                                if (mDataManager.updateQuantity(id, inputQuantity)) {
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
                };
                quantityEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (!hasFocus) {
                            findViewById(R.id.transfer_layout).post(setQuantity);
                        }
                    }
                });
                quantityEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        if (actionId== EditorInfo.IME_ACTION_DONE) {
                            setQuantity.run();
                            quantityEditText.clearFocus();
                            InputMethodManager imm =(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(quantityEditText.getWindowToken(), 0);
                        }
                        return false;
                    }
                });
            }
        }

        public void bindViews(final Cursor cursor, final boolean isSelected) {
            id = cursor.getLong(cursor.getColumnIndexOrThrow(TransferDatabase.Key.ID));
            barcode = cursor.getString(cursor.getColumnIndexOrThrow(TransferDatabase.Key.BARCODE));
            quantity = (int) cursor.getLong(cursor.getColumnIndexOrThrow(TransferDatabase.Key.QUANTITY));
            final AppCompatTextView textBarcode = itemView.findViewById(R.id.text_barcode);
            textBarcode.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            textBarcode.setText(barcode);

            if (mItemRecyclerAdapter.getIsDuplicate(barcode)) {
                itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.item_color_yellow, null));
            } else {
                itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.item_color_white, null));
            }

            if (BuildConfig.display_quantity) {
                final AppCompatEditText quantityEditText = itemView.findViewById(R.id.edit_quantity);
                quantityEditText.setText(cursor.getString(cursor.getColumnIndexOrThrow(TransferDatabase.Key.QUANTITY)));
                quantityEditText.clearFocus();
            }
        }
    }
}
