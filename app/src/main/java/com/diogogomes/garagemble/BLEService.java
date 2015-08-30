package com.diogogomes.garagemble;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.util.Pair;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BLEService extends Service {
    private final static String TAG = BLEService.class.getSimpleName();

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler = new Handler();

    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;

    enum BluetoothCommunicationType {READ, NOTIFY, WRITE}
    private LinkedBlockingQueue<Pair<BluetoothCommunicationType, BluetoothGattCharacteristic>> bluetoothCommandsQueue = new LinkedBlockingQueue<>();

    private HashMap<String, HashMap<String, BluetoothGattCharacteristic>> mGattCharacteristics = new HashMap<String, HashMap<String, BluetoothGattCharacteristic>>();

    public final static String ACTION_GATT_CONNECTED = "com.diogogomes.ble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.diogogomes.ble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.diogogomes.ble.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.diogogomes.ble.ACTION_DATA_AVAILABLE";
    private boolean scanning = false;

    public BLEService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");

        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    public boolean initialize() {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= 21) {
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
            filters = new ArrayList<ScanFilter>();
        }

        scanLeDevice(true);
        return true;
    }

    public void close() {
        Log.i(TAG, "close()");
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
    }

    public void scanLeDevice(final boolean enable) {
        Log.d(TAG, "scanLeDevice("+(enable?"True":"False")+")");
        if (enable) {
            if(scanning) return;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);
                    }
                    scanning = false;
                }
            }, SCAN_PERIOD);
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else {
                mLEScanner.startScan(filters, settings, mScanCallback);
            }
            scanning = true;
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                mLEScanner.stopScan(mScanCallback);
            }
            scanning = false;
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
//            Log.i(TAG, "callbackType " + String.valueOf(callbackType));
//            Log.i(TAG, "result: " +result.toString());
            BluetoothDevice btDevice = result.getDevice();
            connectToDevice(btDevice);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d(TAG, "ScanResult - Results");
            for (ScanResult sr : results) {
                Log.i(TAG, sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan Failed - Error Code: " + errorCode);
        }
    };

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    Log.d(TAG, "onLeScan: " + device.toString());
                    connectToDevice(device);
                }
            };

    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(this, false, gattCallback);
            scanLeDevice(false);// will stop after first device detection
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //Log.d(TAG, "onConnectionStateChange - Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i(TAG, "STATE_CONNECTED");
                    gatt.discoverServices();
                    bluetoothCommandsQueue.clear();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e(TAG, "STATE_DISCONNECTED");
                    bluetoothCommandsQueue.clear();
                    break;
                default:
                    Log.e(TAG, "STATE_OTHER");
            }

        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            //Log.d(TAG, "onDescriptorWrite "+descriptor.getCharacteristic().getUuid());
            bluetoothCommandsQueue.remove();
            processNextInQueue();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            //Log.d(TAG, "onServicesDiscovered() status = " + status);
            List<BluetoothGattService> services = gatt.getServices();
            mGattCharacteristics = new HashMap<>();

            // Loops through available GATT Services.
            for (BluetoothGattService gattService : services) {

                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                HashMap<String,BluetoothGattCharacteristic> charas = new HashMap<>();

                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    charas.put(gattCharacteristic.getUuid().toString(), gattCharacteristic);
                    //Log.d(TAG, "onServicesDiscovered: " + gattCharacteristic.getUuid().toString() + " - Properties: " + gattCharacteristic.getProperties() + " Permissions: " + gattCharacteristic.getPermissions());

                    int properties = gattCharacteristic.getProperties();
                    if (((properties | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) &&
                            ( GaragemGattAttributes.GARAGEM_LAST_OPEN_TS_UUID.equals(gattCharacteristic.getUuid().toString()) ||
                                    GaragemGattAttributes.GARAGEM_LAST_OPEN_ID_UUID.equals(gattCharacteristic.getUuid().toString()))
                            ) {
                        //Log.d(TAG, "notify me of "+gattCharacteristic.getUuid());
                        communicate(BluetoothCommunicationType.NOTIFY, gattCharacteristic);
                    }
                }
                mGattCharacteristics.put(gattService.getUuid().toString(), charas);
            }
            final Intent intent = new Intent(ACTION_GATT_SERVICES_DISCOVERED);
            sendBroadcast(intent);
        }

        private void isGaragemCharacteristics(BluetoothGattCharacteristic characteristic) {
            final Intent intent = new Intent(ACTION_DATA_AVAILABLE);

            if(characteristic.getUuid().toString().equals(GaragemGattAttributes.GARAGEM_LAST_OPEN_TS_UUID)) {
                long timestamp = ByteBuffer.wrap(characteristic.getValue()).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffl;
                intent.putExtra(GaragemGattAttributes.GARAGEM_LAST_OPEN_TS_UUID, timestamp);
            } else if(characteristic.getUuid().toString().equals(GaragemGattAttributes.GARAGEM_LAST_OPEN_ID_UUID)) {
                String id = new String(characteristic.getValue());
                intent.putExtra(GaragemGattAttributes.GARAGEM_LAST_OPEN_ID_UUID, id);
            }
            sendBroadcast(intent);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //Log.i(TAG, "onCharacteristicRead: " + characteristic.getUuid().toString());
            if (status == BluetoothGatt.GATT_SUCCESS) {
                final Intent intent = new Intent(ACTION_DATA_AVAILABLE);
                if (characteristic.getUuid().toString().equals(GaragemGattAttributes.SECURITY_IV_CHARACTERISTIC_UUID)) {
                    intent.putExtra(GaragemGattAttributes.SECURITY_IV_CHARACTERISTIC_UUID, characteristic.getValue());
                    sendBroadcast(intent);
                } else {
                    isGaragemCharacteristics(characteristic);
                }
            }
            bluetoothCommandsQueue.remove();
            processNextInQueue();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            //Log.d(TAG, "onCharacteristicWrite()");
            bluetoothCommandsQueue.remove();
            processNextInQueue();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
        {
            Log.d(TAG, "onCharacteristicChanged("+characteristic.getUuid()+")");
            isGaragemCharacteristics(characteristic);
        }


    };

    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mGatt == null) {
            Log.e(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        boolean status = mGatt.setCharacteristicNotification(characteristic, enabled);
        if(!status) return false;

        if (GaragemGattAttributes.GARAGEM_LAST_OPEN_TS_UUID.equals(characteristic.getUuid().toString())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(GaragemGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            status = mGatt.writeDescriptor(descriptor);
        } else if (GaragemGattAttributes.GARAGEM_LAST_OPEN_ID_UUID.equals(characteristic.getUuid().toString())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(GaragemGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            status = mGatt.writeDescriptor(descriptor);
        }
        return status;
    }

    private boolean communicate(BluetoothCommunicationType type, BluetoothGattCharacteristic characteristic) {
        try {
            bluetoothCommandsQueue.put(new Pair<BluetoothCommunicationType, BluetoothGattCharacteristic>(type, characteristic));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(bluetoothCommandsQueue.size() > 1) return true;

        //Log.d(TAG, "Last BT communication on the queue");
        boolean status = false;

        if(characteristic == null) {
            Log.e(TAG, "Null characteristic !?");
            return false;
        }

        switch (type) {
            case WRITE:
                status= mGatt.writeCharacteristic(characteristic);
                break;
            case READ:
                status = mGatt.readCharacteristic(characteristic);
                break;
            case NOTIFY:
                setCharacteristicNotification(characteristic, true);

        }

        return status;
    }

    private void processNextInQueue() {
        if(bluetoothCommandsQueue.isEmpty()) return;

        Pair<BluetoothCommunicationType, BluetoothGattCharacteristic> e = bluetoothCommandsQueue.element();

        switch (e.first) {
            case READ:
                if(!mGatt.readCharacteristic(e.second)) {// Skiping characteristics that fail (usually due to permissions...)
                    Log.e(TAG, "error reading " + e.second.getUuid());
                    bluetoothCommandsQueue.remove();
                    processNextInQueue();
                }
                break;
            case WRITE:
                if(!mGatt.writeCharacteristic(e.second)) {
                    Log.e(TAG, "error writing " + e.second.getUuid());
                    bluetoothCommandsQueue.remove();
                    processNextInQueue();
                }
                break;
            case NOTIFY:
                if(!setCharacteristicNotification(e.second, true)) {
                    Log.e(TAG, "error set notification " + e.second.getUuid());
                    bluetoothCommandsQueue.remove();
                    processNextInQueue();
                }
        }
    }

    public void replyChallenge(byte [] challenge) {
        if(mGattCharacteristics==null)
            return;
        BluetoothGattCharacteristic c = mGattCharacteristics.get(GaragemGattAttributes.GARAGEM_SERVICE).get(GaragemGattAttributes.GARAGEM_CHALLENGE_CHARACTERISTIC_UUID);
        c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        c.setValue(challenge);
        communicate(BluetoothCommunicationType.WRITE, c);
    }

    public void voidChallenge() {
        if(mGattCharacteristics==null)
            return;
        BluetoothGattCharacteristic c = mGattCharacteristics.get(GaragemGattAttributes.GARAGEM_SERVICE).get(GaragemGattAttributes.GARAGEM_CHALLENGE_CHARACTERISTIC_UUID);
        c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        c.setValue(new byte[16]); //dummy just to trigger TS and ID change
        communicate(BluetoothCommunicationType.WRITE, c);
    }

    public void getLastOpenID() {
        Log.d(TAG, "getLastOpenID()");
        if(mGattCharacteristics==null)
            return;
        BluetoothGattCharacteristic id = mGattCharacteristics.get(GaragemGattAttributes.GARAGEM_SERVICE).get(GaragemGattAttributes.GARAGEM_LAST_OPEN_ID_UUID);
        communicate(BluetoothCommunicationType.READ, id);
    }
    public void getLastOpenTS() {
        Log.d(TAG, "getLastOpenTS()");
        if(mGattCharacteristics==null)
            return;
        BluetoothGattCharacteristic ts = mGattCharacteristics.get(GaragemGattAttributes.GARAGEM_SERVICE).get(GaragemGattAttributes.GARAGEM_LAST_OPEN_TS_UUID);
        communicate(BluetoothCommunicationType.READ, ts);
    }

    void getIV() {
        Log.d(TAG, "getIV()");
        if(mGattCharacteristics==null)
            return;
        BluetoothGattCharacteristic c = mGattCharacteristics.get(GaragemGattAttributes.SECURITY_SERVICE).get(GaragemGattAttributes.SECURITY_IV_CHARACTERISTIC_UUID);
        communicate(BluetoothCommunicationType.READ, c);
    }

    public void setSharedKey(String sharedKey) {
        if(mGattCharacteristics==null)
            return;
        BluetoothGattCharacteristic c = mGattCharacteristics.get(GaragemGattAttributes.SECURITY_SERVICE).get(GaragemGattAttributes.SECURITY_KEY_CHARACTERISTIC_UUID);
        c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        c.setValue(sharedKey.getBytes());
        communicate(BluetoothCommunicationType.WRITE, c);
    }

    public class LocalBinder extends Binder {
        BLEService getService() {
            return BLEService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

}
