package com.porterlee.limstransfer;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.widget.AppCompatButton;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
                        mDataManager.showDialog(new AlertDialog.Builder(TransferActivity.this)
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

    public void openFinalizeDialog(final Utils.OnFinishListener onFinishListener) {
        mDataManager.showDialog(new AlertDialog.Builder(this)
                .setTitle("Finalize transfer")
                .setMessage(
                        "Would you like to finalize this transfer?" +
                        (mDataManager.getCurrentTransfer().isSigned() ?
                                "" :
                                "\n" +
                                "\n" +
                                "Note: the current transfer has not been signed"
                        )
                ).setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_finalize, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mDataManager.saveTransferToFile(TransferActivity.this, new Utils.OnProgressUpdateListener() {
                            @Override
                            public void onProgressUpdate(float progress) {
                                final MaterialProgressBar progressBar = findViewById(R.id.progress_bar);
                                progressBar.setProgress((int) (progress * progressBar.getMax()));
                            }
                        }, new Utils.OnFinishListener() {
                            @Override
                            public void onFinish(boolean success) {
                                TransferActivity.this.<MaterialProgressBar>findViewById(R.id.progress_bar).setProgress(0);
                                success &= mDataManager.finalizeCurrentTransfer();
                                onFinishListener.onFinish(success);
                            }
                        });
                    }
                }).setCancelable(false).create(), null);
    }

    private void openSignDialog(final DataManager.InternalOnFinishListener onFinishListener) {
        final AppCompatDialog compatDialog = new AppCompatDialog(this, R.style.CustomDialogTheme);

        if (compatDialog.getWindow() != null) {
            compatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            compatDialog.setCancelable(false);
            compatDialog.setContentView(R.layout.fragment_sign);

            View v = compatDialog.getWindow().getDecorView();
            final SignaturePad signaturePad = v.findViewById(R.id.signature_pad);
            final AppCompatButton buttonClear = v.findViewById(R.id.button_clear);
            final AppCompatButton buttonCancel = v.findViewById(R.id.button_cancel);
            final AppCompatButton buttonSave = v.findViewById(R.id.button_save);

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

            buttonClear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    signaturePad.clear();
                }
            });
            buttonCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onFinishListener.onFinish(false, "Signing canceled");
                    compatDialog.dismiss();
                }
            });
            buttonSave.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mDataManager.saveSignature(TransferActivity.this, signaturePad.getSignatureBitmap(), mDataManager.getCurrentTransfer(), onFinishListener);
                    compatDialog.dismiss();
                }
            });

            mDataManager.showDialog(compatDialog, null);
        }
    }

    private void openAnalystLoginDialog(final DataManager.InternalOnFinishListener onFinishListener) {
        final AppCompatDialog compatDialog = new AppCompatDialog(this, R.style.CustomDialogTheme);

        if (compatDialog.getWindow() != null) {
            compatDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            compatDialog.setCancelable(false);
            compatDialog.setContentView(R.layout.fragment_login);

            View v = compatDialog.getWindow().getDecorView();
            final AppCompatEditText editAnalystId = v.findViewById(R.id.edit_analyst_id);
            final AppCompatEditText editAnalystPassword = v.findViewById(R.id.edit_analyst_password);
            final AppCompatButton buttonCancel = v.findViewById(R.id.button_cancel);
            final AppCompatButton buttonLogin = v.findViewById(R.id.button_login);

            editAnalystId.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) { }

                @Override
                public void afterTextChanged(Editable s) {
                    buttonLogin.setEnabled(!s.toString().equals("") && !editAnalystPassword.getText().toString().equals(""));
                }
            });
            editAnalystPassword.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) { }

                @Override
                public void afterTextChanged(Editable s) {
                    buttonLogin.setEnabled(!s.toString().equals("") && !editAnalystId.getText().toString().equals(""));
                }
            });
            buttonCancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onFinishListener.onFinish(false, "Login canceled");
                    compatDialog.dismiss();
                }
            });
            buttonLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DataManager.Analyst analyst = mDataManager.getAnalyst(editAnalystId.getText().toString());
                    analyst.verifyAnalystLogin(editAnalystPassword.getText().toString());
                    compatDialog.dismiss();
                }
            });

            mDataManager.showDialog(compatDialog, null);
        }
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
                } else {
                    if (mDataManager.requiresAnalystLogin()) {
                        openAnalystLoginDialog(new DataManager.InternalOnFinishListener() {
                            @Override
                            public void onFinish(boolean success, String message) {
                                if (success) {
                                    toastShort(message);
                                    if (mDataManager.requiresSignature()) {
                                        openSignDialog(new DataManager.InternalOnFinishListener() {
                                            @Override
                                            public void onFinish(boolean success, String message) {
                                                if (success) {
                                                    toastShort(message);
                                                    openFinalizeDialog(new Utils.OnFinishListener() {
                                                        @Override
                                                        public void onFinish(boolean success) {
                                                            if (success) {
                                                                toastShort("Finalized");
                                                            } else {
                                                                toastLong("There was an error finalizing");
                                                            }
                                                        }
                                                    });
                                                } else {
                                                    toastLong(message);
                                                }
                                            }
                                        });
                                    } else {
                                        openFinalizeDialog(new Utils.OnFinishListener() {
                                            @Override
                                            public void onFinish(boolean success) {
                                                if (success) {
                                                    toastShort("Finalized");
                                                } else {
                                                    toastLong("There was an error finalizing");
                                                }
                                            }
                                        });
                                    }
                                } else {
                                    toastLong(message);
                                }
                            }
                        });
                    } else {
                        if (mDataManager.requiresSignature()) {
                            openSignDialog(new DataManager.InternalOnFinishListener() {
                                @Override
                                public void onFinish(boolean success, String message) {
                                    if (success) {
                                        toastShort(message);
                                        openFinalizeDialog(new Utils.OnFinishListener() {
                                            @Override
                                            public void onFinish(boolean success) {
                                                if (success) {
                                                    toastShort("Finalized");
                                                } else {
                                                    toastLong("There was an error finalizing");
                                                }
                                            }
                                        });
} else {
                                        toastLong(message);
                                    }
                                }
                            });
                        } else {
                            openFinalizeDialog(new Utils.OnFinishListener() {
                                @Override
                                public void onFinish(boolean success) {
                                    if (success) {
                                        toastShort("Finalized");
                                    } else {
                                        toastLong("There was an error finalizing");
                                    }
                                }
                            });
                        }
                    }
                }
                return true;
            case R.id.menu_cancel:
                if (mDataManager.getCurrentTransfer() == null) {
                    toastShort("Start a transfer first");
                } else if (mDataManager.getCurrentTransfer().isFinalized()) {
                    toastShort("Already finalized");
                } else if (mDataManager.getCurrentTransfer().isCanceled()) {
                    toastShort("Already canceled");
                } else {
                    mDataManager.showDialog(new AlertDialog.Builder(this)
                            .setTitle("Cancel transfer")
                            .setMessage("Would you like to cancel this transfer?")
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
                return true;
            case R.id.menu_reset:
                mDataManager.showDialog(new AlertDialog.Builder(this)
                        .setTitle("Reset transfers")
                        .setMessage("Would you like to clear all transfer output and signatures?")
                        .setNegativeButton(R.string.action_no, null)
                        .setPositiveButton(R.string.action_yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mDataManager.reset(getApplicationContext());
                            }
                        }).create(), null);
                return true;
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
        AbstractScanner.setOnBarcodeScannedListener(onBarcodeScannedListener);
        AbstractScanner.setActivity(this);
        refreshItemRecyclerAdapter();
    }

    @Override
    protected void onPause() {
        AbstractScanner.setActivity(null);
        AbstractScanner.setOnBarcodeScannedListener(null);
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
        mDataManager.showDialog(new AlertDialog.Builder(TransferActivity.this)
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
        final boolean isNull = !isLocation && !isContainer;
        this.<AppCompatTextView>findViewById(R.id.text_current_location_label).setText(getResources().getString(R.string.text_current_location_label, isNull ? "-" : (isLocation ? "Location" : "Container")));
        this.<AppCompatTextView>findViewById(R.id.text_current_location).setText(isNull ? "-" : location);
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
                            mDataManager.showDialog(new AlertDialog.Builder(TransferActivity.this)
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
        }

        public void bindViews(final Cursor cursor, final boolean isSelected) {
            id = cursor.getLong(cursor.getColumnIndexOrThrow(TransferDatabase.Key.ID));
            barcode = cursor.getString(cursor.getColumnIndexOrThrow(TransferDatabase.Key.BARCODE));
            final AppCompatTextView textBarcode = itemView.findViewById(R.id.text_barcode);
            textBarcode.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            textBarcode.setText(barcode);

            if (mItemRecyclerAdapter.getIsDuplicate(barcode)) {
                itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.item_color_yellow, null));
            } else {
                itemView.setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.item_color_white, null));
            }
        }
    }
}
