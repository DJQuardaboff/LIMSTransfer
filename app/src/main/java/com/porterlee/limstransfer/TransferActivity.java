package com.porterlee.limstransfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import device.scanner.DecodeResult;
import device.scanner.IScannerService;
import device.scanner.ScanConst;

public class TransferActivity extends AppCompatActivity {
    private CursorRecyclerViewAdapter<TransferItemViewHolder> mItemRecyclerAdapter;
    private volatile SQLiteDatabase mDatabase;
    private ScanResultReceiver mResultReciever;
    private IScannerService mScanner = null;
    private DecodeResult mDecodeResult = new DecodeResult();

    private BroadcastReceiver mScanKeyEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ScanConst.INTENT_SCANKEY_EVENT.equals(intent.getAction())) {
                KeyEvent event = intent.getParcelableExtra(ScanConst.EXTRA_SCANKEY_EVENT);
                switch (event.getKeyCode()) {
                    case ScanConst.KEYCODE_SCAN_FRONT:
                        onScanKeyEvent(event.getAction());
                        break;
                    case ScanConst.KEYCODE_SCAN_LEFT:
                        onScanKeyEvent(event.getAction());
                        break;
                    case ScanConst.KEYCODE_SCAN_RIGHT:
                        onScanKeyEvent(event.getAction());
                        break;
                    case ScanConst.KEYCODE_SCAN_REAR:
                        onScanKeyEvent(event.getAction());
                        break;
                }
            }
        }
    };

    private void onScanKeyEvent(int action) {
        if (mScanner != null) {
            try {
                if (action == KeyEvent.ACTION_DOWN) {
                    mScanner.aDecodeSetTriggerOn(1);
                } else if (action == KeyEvent.ACTION_UP) {
                    mScanner.aDecodeSetTriggerOn(0);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);

        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(String.format("%1s v%2s", getString(R.string.app_name), BuildConfig.VERSION_NAME));

        this.<AppCompatButton>findViewById(R.id.test_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { updateInfo("", 0); }
        });

        this.<TextView>findViewById(R.id.current_location_text_view).setText("-");
        this.<TextView>findViewById(R.id.item_count_text_view).setText("-");
    }

    private void updateInfo(String location, int itemCount) {
        this.<TextView>findViewById(R.id.current_location_text_view).setText(location);
        this.<TextView>findViewById(R.id.item_count_text_view).setText(String.valueOf(itemCount));
    }

    private class TransferItemViewHolder extends RecyclerView.ViewHolder {
        public TransferItemViewHolder(View itemView) {
            super(itemView);
            final AppCompatImageButton expandedMenuButton = itemView.findViewById(R.id.expanded_menu);
            expandedMenuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final PopupMenu popup = new PopupMenu(TransferActivity.this, expandedMenuButton);
                    popup.getMenuInflater().inflate(R.menu.menu_transfer_item, popup.getMenu());
                    final MenuItem item = popup.getMenu().findItem(R.id.menu_remove);
                    item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {

                            return true;
                        }
                    });
                    popup.show();
                }
            });
        }

        public void bindViews(Cursor cursor) {
            itemView.findViewById(R.id.barcode_text_view);
        }
    }

    private void onBarcodeScanned(String barcode) {

    }

    private class ScanResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mScanner != null) {
                try {
                    mScanner.aDecodeGetResult(mDecodeResult);
                    String barcode = mDecodeResult.decodeValue;
                    if (!barcode.equals("SCAN AGAIN"))
                        onBarcodeScanned(barcode);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
