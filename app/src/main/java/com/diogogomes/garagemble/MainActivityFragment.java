package com.diogogomes.garagemble;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.Button;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivityFragment extends Fragment {
    private final static String TAG = MainActivityFragment.class.getSimpleName();

    private int REQUEST_ENABLE_BT = 1;

    private BLEService mBluetoothLeService = null;

    private byte[] iv = null;

    public MainActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent gattServiceIntent = new Intent(getActivity(), BLEService.class);
        getActivity().getApplicationContext().bindService(gattServiceIntent, mServiceConnection, getActivity().BIND_AUTO_CREATE);
        getActivity().getApplicationContext().registerReceiver(mLeServiceReceiver, makeGattUpdateIntentFilter());
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
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        getActivity().getApplicationContext().registerReceiver(mLeServiceReceiver, makeGattUpdateIntentFilter());

        if(mBluetoothLeService!=null)
            mBluetoothLeService.initialize();
    }

    private void buttonAvailable(boolean available) {
        final Button button = (Button) getActivity().findViewById(R.id.garagemButton);
        if(available) {
            button.setEnabled(true);
            button.setText(getResources().getString(R.string.main_button_open));
        } else {
            button.setEnabled(false);
            button.setText(getResources().getString(R.string.main_button_searching));
        }
    }

    @Override
    public void onPause() {
        getActivity().getApplicationContext().unregisterReceiver(mLeServiceReceiver);
        if(mBluetoothLeService != null) {
            mBluetoothLeService.close();
        }
        buttonAvailable(false);
        super.onPause();
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            mBluetoothLeService = ((BLEService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                Log.d(TAG, "requiring user to turn ON bluetooth");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected");
            mBluetoothLeService = null;
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                getActivity().finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View fragmentView = inflater.inflate(R.layout.fragment_main, container, false);

        final Button button = (Button) fragmentView.findViewById(R.id.garagemButton);
        button.setEnabled(false);
        button.setText(getResources().getString(R.string.main_button_searching));

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBluetoothLeService.getIV();    //on response will trigger challenge response
            }
        });

        return fragmentView;
    }

    private void respondToChallenge() throws RuntimeException {
        if(iv == null) {
            mBluetoothLeService.scanLeDevice(true);
            throw new RuntimeException("IV not initialized");
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        String sharedKeyString = preferences.getString(getResources().getString(R.string.pref_key_shared_key), "");
        String idString = preferences.getString(getResources().getString(R.string.pref_key_id), "none");
        for(int i=idString.length(); i<4;i++)
            idString+=" ";
        Log.d(TAG, "keyStr = "+sharedKeyString);
        Log.d(TAG, "id = " + idString);


        try {
            byte[] key = sharedKeyString.getBytes("UTF-8");
            MessageDigest sha = MessageDigest.getInstance("MD5");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // use only first 128 bit
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            /* DEBUG
            byte [] keySpec = secretKeySpec.getEncoded();
            Log.d("TAG", "Next it's our key");
            logByteArray(keySpec);
            */

            byte [] id = idString.getBytes("UTF-8");
            int currentTime = (int) (System.currentTimeMillis()/1000);
            byte [] timestamp = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(currentTime).array();
            byte [] sysKey = {'A','B','R','E','-','T','E','\0'}; //TODO we would be better off using a longer ID and a CRC...

            byte [] challenge = new byte[16];
            System.arraycopy(timestamp, 0, challenge, 0, 4);
            System.arraycopy(id, 0, challenge, 4, 4);
            System.arraycopy(sysKey, 0, challenge, 8, 8);
//            logByteArray(challenge);

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(challenge);
            Log.d(TAG, "challenge: ");
            logByteArray(encrypted);

            mBluetoothLeService.replyChallenge(encrypted);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unsupported Encoding");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No such Algorithm");
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException("No such Padding");
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException("Illegal Block Size");
        } catch (BadPaddingException e) {
            throw new RuntimeException("Bad Padding");
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Invalid Algorithm Parameter");
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Invalid Key");
        }

    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mLeServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "BroadcastReceiver = " + action);
            if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mBluetoothLeService.close();
            } else if (BLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                buttonAvailable(true);
            } else if (BLEService.ACTION_DATA_AVAILABLE.equals(action)) {
                if (intent.hasExtra(GaragemGattAttributes.SECURITY_IV_CHARACTERISTIC_UUID)) {
                    iv = intent.getByteArrayExtra(GaragemGattAttributes.SECURITY_IV_CHARACTERISTIC_UUID);
                    Log.d(TAG, "next it's the IV");
                    logByteArray(iv);

                    if(java.util.Arrays.equals(iv, new byte[16])) {
                        Log.i(TAG, "Initializing physical device");
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        mBluetoothLeService.setSharedKey(preferences.getString(getResources().getString(R.string.pref_key_shared_key), ""));
                    }

                    respondToChallenge();
                } else if (intent.hasExtra(GaragemGattAttributes.GARAGEM_LAST_OPEN_TS_UUID)) {
                    long timestamp = intent.getLongExtra(GaragemGattAttributes.GARAGEM_LAST_OPEN_TS_UUID, 0);
                    Date ts = new Date(timestamp * 1000); //convert to ms 1st
                    Log.d(TAG, "Timestamp: " + ts.toString());
                } else if (intent.hasExtra(GaragemGattAttributes.GARAGEM_LAST_OPEN_ID_UUID)) {
                    String id = intent.getStringExtra(GaragemGattAttributes.GARAGEM_LAST_OPEN_ID_UUID);
                    Log.d(TAG, "ID: "+id);

                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    Toast.makeText(getActivity(), getString(R.string.welcome)+" "+preferences.getString(getResources().getString(R.string.pref_key_id), "N/A"), Toast.LENGTH_SHORT).show();

                }
            }
        }
    };

    private void logByteArray(byte[] array) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : array) {
            builder.append(String.format("%02x", b));
        }
        Log.d(TAG, builder.toString());
    }
}
