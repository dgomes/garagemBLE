package com.diogogomes.garagemble;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TableRow.LayoutParams;

import java.util.Date;

public class DebugActivity extends AppCompatActivity {
    private final static String TAG = DebugActivity.class.getSimpleName();
    private static final int NUM_RECORDS = 16;

    private BLEService mBluetoothLeService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);


        Intent gattServiceIntent = new Intent(this, BLEService.class);
        getApplicationContext().bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        getApplicationContext().registerReceiver(mLeServiceReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        getApplicationContext().registerReceiver(mLeServiceReceiver, makeGattUpdateIntentFilter());

        if(mBluetoothLeService!=null)
            mBluetoothLeService.initialize();

        TableLayout tl = (TableLayout) findViewById(R.id.debugTableLayout);
        tl.removeAllViewsInLayout();

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void onPause() {
        getApplicationContext().unregisterReceiver(mLeServiceReceiver);
        if(mBluetoothLeService != null) {
            mBluetoothLeService.close();
        }
        super.onPause();
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            mBluetoothLeService = ((BLEService.LocalBinder) service).getService();
            mBluetoothLeService.initialize();

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected");
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mLeServiceReceiver = new BroadcastReceiver() {
        private Date ts = null;
        private String id = null;
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "BroadcastReceiver = " + action);
            if (BLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                for(int i=0; i<NUM_RECORDS; i++)
                    mBluetoothLeService.voidChallenge();
            } if (BLEService.ACTION_DATA_AVAILABLE.equals(action)) {
                if (intent.hasExtra(GaragemGattAttributes.GARAGEM_LAST_OPEN_TS_UUID)) {
                    long timestamp = intent.getLongExtra(GaragemGattAttributes.GARAGEM_LAST_OPEN_TS_UUID, 0);
                    ts = new Date(timestamp * 1000); //convert to ms 1st
                } else if (intent.hasExtra(GaragemGattAttributes.GARAGEM_LAST_OPEN_ID_UUID)) {
                    id = intent.getStringExtra(GaragemGattAttributes.GARAGEM_LAST_OPEN_ID_UUID);
                }

                if(ts!=null && id!=null) {
                    Log.d(TAG, "Timestamp: " + ts.toString() + "\tId: "+ id);
                    createTableRow(ts.toGMTString(), id);
                    ts = null;
                    id = null;
                }
            }
        }
    };

    public void createTableRow(String timestamp, String id) {
        TableLayout tl = (TableLayout) findViewById(R.id.debugTableLayout);
        TableRow tr = new TableRow(this);
        TableRow.LayoutParams lp_row = new TableRow.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        TableRow.LayoutParams lp_text = new TableRow.LayoutParams(LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);

        tr.setLayoutParams(lp_row);

        TextView tvTimeStamp = new TextView(this);
        tvTimeStamp.setLayoutParams(lp_text);
        tvTimeStamp.setText(timestamp);
        tvTimeStamp.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        TextView tvID = new TextView(this);
        tvID.setLayoutParams(lp_text);
        tvID.setText("\t\t" + id);
        tvID.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);

        tr.addView(tvTimeStamp);
        tr.addView(tvID);

        tl.addView(tr, new TableLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        tl.requestLayout();
    }

}
