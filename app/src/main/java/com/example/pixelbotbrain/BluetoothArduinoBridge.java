package com.example.pixelbotbrain;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

/**
 * Provides a simple Bluetooth bridge to an HC-05 bluetooth module connected to an Arduino.
 * Uses a simple byte protocol: COMMAND_NUM NUM_VALS VAL1 VAL2 ... VALN
 * Java byte (which recall are signed) maps to Arduino's int8_t
 *
 * Specify the MAC address of your HC-05 with DEViCE_ADDRESS.
 *
 * Written by Dave Burke (2018)
 */
public class BluetoothArduinoBridge {
    private static final String TAG = "BluetoothArduinoBridge";
    private static final String DEVICE_ADDRESS = "20:16:12:12:70:84";  // my HC-05 address
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private Listener mListener;
    private Thread mConnectingThread;
    private Handler mHandler;
    private BluetoothDevice mDevice;
    private BluetoothSocket mSocket;
    private boolean mConnecting;

    public interface Listener {
        public void onBluetoothConnected();
        public void onBluetoothConnectionFailed(final String errorMsg);
    }

    public void connectAsync(Listener listener) {
        if (mConnecting) return;
        mConnecting = true;

        mListener = listener;
        mHandler = new Handler();

        mConnectingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Connecting ...");

                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter == null) {
                    postOnBluetoothConnectionFailed("Can't get Bluetooth adaptor");
                    return;
                }

                if (!bluetoothAdapter.isEnabled()) {
                    postOnBluetoothConnectionFailed("Bluetooth not enabled");
                    return;
                }

                Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
                if (bondedDevices.isEmpty()) {
                    postOnBluetoothConnectionFailed("No devices paired");
                    return;
                }

                mDevice = null;
                for (BluetoothDevice iterator : bondedDevices) {
                    if (iterator.getAddress().equals(DEVICE_ADDRESS)) {
                        Log.d(TAG, "Found HC-05 " + iterator.getAddress());
                        mDevice = iterator;
                    }
                }
                if (mDevice == null) {
                    postOnBluetoothConnectionFailed("Unable to get BT device");
                    return;
                }

                while (mConnecting) {
                    try {
                        Log.d(TAG, "Attempting to create RFComm socket");
                        mSocket = mDevice.createRfcommSocketToServiceRecord(PORT_UUID);
                        mSocket.connect();
                        postOnBluetoothConnected();
                    } catch (IOException e) {
                        try {
                            Log.w(TAG, e.getMessage());
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) { }
                    }
                }
            }
        });
        mConnectingThread.start();
    }

    public void disconnect() {
        if (mConnecting) {
            mConnecting = false;
            try {
                mConnectingThread.join();
            } catch (InterruptedException e) { }
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    protected void writeData(byte cmd, byte vals[]) {
        OutputStream outputStream = null;
        try {
            outputStream = mSocket.getOutputStream();
            byte[] data = new byte[2 + vals.length];
            data[0] = cmd;
            data[1] = (byte) vals.length;
            for (int i = 0; i < vals.length; i++) {
                data[i+2] = vals[i];
            }
            outputStream.write(data);
        } catch (IOException e) {
            mListener.onBluetoothConnectionFailed(e.getMessage());
            connectAsync(mListener);  // for robustness, try to automatically reconnect
        }
    }

    private void postOnBluetoothConnectionFailed(final String errorMsg) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onBluetoothConnectionFailed(errorMsg);
            }
        });
        mConnecting = false;
    }

    private void postOnBluetoothConnected() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onBluetoothConnected();
            }
        });
        mConnecting = false;
    }
}
