/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package emgsignal.v3;

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
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.List;
import java.util.UUID;

public class UartService extends Service {
    public final static String TAG = UartService.class.getSimpleName();

    BluetoothManager mBluetoothManager;
    BluetoothAdapter mBluetoothAdapter;

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.nordicsemi.nrfUART.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.nordicsemi.nrfUART.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.nordicsemi.nrfUART.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.nordicsemi.nrfUART.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.nordicsemi.nrfUART.EXTRA_DATA";
    public final static String DEVICE_DOES_NOT_SUPPORT_UART =
            "com.nordicsemi.nrfUART.DEVICE_DOES_NOT_SUPPORT_UART";

    public final static String EXTRA_DATA_BLE1 =
            "com.nordicsemi.nrfUART.EXTRA_DATA_BLE1";
    public final static String EXTRA_DATA_BLE2 =
            "com.nordicsemi.nrfUART.EXTRA_DATA_BLE2";

    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    public BluetoothGatt mBluetoothGattBle1;
    public BluetoothGatt mBluetoothGattBle2;

    private int mConnectionStateBle1 = STATE_DISCONNECTED;
    private int mConnectionStateBle2 = STATE_DISCONNECTED;

    String mBluetoothDeviceAddressBLE1 ="";
    String mBluetoothDeviceAddressBLE2 ="";

    public static final UUID CCCD_BLE1 = UUID.fromString("00002903-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_SERVICE_UUID_BLE1 = UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_CHAR_UUID_BLE1 = UUID.fromString("6e400005-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TX_CHAR_UUID_BLE1 = UUID.fromString("6e400006-b5a3-f393-e0a9-e50e24dcca9e");

    public static final UUID CCCD_BLE2 = UUID.fromString("00002904-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_SERVICE_UUID_BLE2 = UUID.fromString("00001110-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_CHAR_UUID_BLE2 = UUID.fromString("6e400008-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TX_CHAR_UUID_BLE2 = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb");


    public final BluetoothGattCallback mGattCallback1 = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;

            if(newState == BluetoothProfile.STATE_CONNECTED){
                intentAction = ACTION_GATT_CONNECTED;
                synchronized (mGattCallback1){
                    mConnectionStateBle1 = STATE_CONNECTED;
                }
                mBluetoothGattBle1.discoverServices();
                broadcastUpdate(intentAction);
            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                intentAction = ACTION_GATT_DISCONNECTED;
                synchronized (mGattCallback1){
                    mConnectionStateBle1 = STATE_DISCONNECTED;
                }
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mBluetoothGattBle1 = gatt;
            if (status == BluetoothGatt.GATT_SUCCESS) {

                BluetoothGattService mBluetoothGattService1 = mBluetoothGattBle1.getService(RX_SERVICE_UUID);
                if (mBluetoothGattService1 != null) {
                    Log.i(TAG, "Service characteristic UUID found: " + mBluetoothGattService1.getUuid());
                } else {
                    Log.i(TAG, "Service characteristic not found for UUID: " + RX_SERVICE_UUID);
                }

                Log.w(TAG, "mBluetoothGatt = " + mBluetoothGattBle1 );

                BluetoothGattCharacteristic characteristicTxBle1 = mBluetoothGattService1.getCharacteristic(TX_CHAR_UUID);

                if (characteristicTxBle1 == null) {
                    showMessage("Tx charateristic not found!");
                    broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
                    return;
                }
                mBluetoothGattBle1.setCharacteristicNotification(characteristicTxBle1, true);
                mBluetoothGattBle1.readCharacteristic(characteristicTxBle1);

                BluetoothGattDescriptor descriptor_ble1 = characteristicTxBle1.getDescriptor(CCCD);
                descriptor_ble1.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGattBle1.writeDescriptor(descriptor_ble1);
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.i("status1", "success");
                broadcastUpdate(ACTION_DATA_AVAILABLE , characteristic , gatt);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE , characteristic , gatt);
        }
    };

    public final BluetoothGattCallback mGattCallback2 = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;

            if(newState == BluetoothProfile.STATE_CONNECTED){
                intentAction = ACTION_GATT_CONNECTED;
                synchronized (mGattCallback2){
                    mConnectionStateBle2 = STATE_CONNECTED;
                }
                broadcastUpdate(intentAction);
                mBluetoothGattBle2.discoverServices();
            }else if(newState == BluetoothProfile.STATE_DISCONNECTED){
                intentAction = ACTION_GATT_DISCONNECTED;
                synchronized (mGattCallback2){
                    mConnectionStateBle2 = STATE_DISCONNECTED;
                }
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mBluetoothGattBle2 = gatt;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService mBluetoothGattService2 = mBluetoothGattBle2.getService(RX_SERVICE_UUID);
                if (mBluetoothGattService2 != null) {
                    Log.i(TAG, "Service characteristic UUID found: " + mBluetoothGattService2.getUuid().toString());
                } else {
                    Log.i(TAG, "Service characteristic not found for UUID: " + RX_SERVICE_UUID);
                }

                Log.w(TAG, "mBluetoothGatt = " + mBluetoothGattBle2 );

                BluetoothGattCharacteristic characteristicTxBle2 = mBluetoothGattService2.getCharacteristic(TX_CHAR_UUID);
                if (characteristicTxBle2 == null) {
                    Log.d("charac2", "no characteristic was found");
                    showMessage("Tx charateristic not found!");
                    broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
                    return;
                }
                mBluetoothGattBle2.setCharacteristicNotification(characteristicTxBle2, true);
                mBluetoothGattBle2.readCharacteristic(characteristicTxBle2);

                BluetoothGattDescriptor descriptor_ble2 = characteristicTxBle2.getDescriptor(CCCD);
                descriptor_ble2.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mBluetoothGattBle2.writeDescriptor(descriptor_ble2);
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                broadcastUpdate(ACTION_DATA_AVAILABLE , characteristic , gatt);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE , characteristic , gatt);
        }
    };



    public void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic , BluetoothGatt gatt) {
        final Intent intent = new Intent(action);

        if (TX_CHAR_UUID.equals(characteristic.getUuid())  && mBluetoothGattBle1 == gatt) {
            intent.putExtra(EXTRA_DATA_BLE1 , characteristic.getValue());
        } else if(TX_CHAR_UUID.equals(characteristic.getUuid()) && mBluetoothGattBle2 == gatt) {
            intent.putExtra(EXTRA_DATA_BLE2 , characteristic.getValue());
        }else{
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        UartService getService() {
            return UartService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.w(TAG, "UNBINDING");
        close();
        return super.onUnbind(intent);
    }

    public final IBinder mBinder = new LocalBinder();


    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public boolean connect(final String address) {
        Log.d("case1", "trying to connect");
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddressBLE1 != null && address.equals(mBluetoothDeviceAddressBLE1)
                && mBluetoothGattBle1 != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt_1 for connection.");
            if (mBluetoothGattBle1.connect()) {
                mConnectionStateBle1 = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        if (mBluetoothDeviceAddressBLE2 != null && address.equals(mBluetoothDeviceAddressBLE2)
                && mBluetoothGattBle2 != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt_1 for connection.");
            if (mBluetoothGattBle2.connect()) {
                mConnectionStateBle2 = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        if(mBluetoothGattBle1 == null && mBluetoothGattBle2 == null){
            mBluetoothGattBle1 = device.connectGatt(this, false, mGattCallback1);
            mBluetoothDeviceAddressBLE1 = address;
            mConnectionStateBle1 = STATE_CONNECTING;
        }else if(mBluetoothGattBle1 != null && mBluetoothGattBle2 == null){
            mBluetoothGattBle2 = device.connectGatt(this, false, mGattCallback2);
            mBluetoothDeviceAddressBLE2 = address;
            mConnectionStateBle2 = STATE_CONNECTING;
        }else if(mBluetoothGattBle1 == null && mBluetoothGattBle2 != null){
            mBluetoothGattBle1 = device.connectGatt(this, false, mGattCallback1);
            mBluetoothDeviceAddressBLE1 = address;
            mConnectionStateBle1 = STATE_CONNECTING;
        }else{

        }
        return true;
    }


    public void close1() {
        if (mBluetoothGattBle1 != null) {
            mBluetoothDeviceAddressBLE1 = null;
            mBluetoothGattBle1.close();
            mBluetoothGattBle1 = null;
        }
    }
    public void close2() {
        if (mBluetoothGattBle2 != null) {
            mBluetoothDeviceAddressBLE2 = null;
            mBluetoothGattBle2.close();
            mBluetoothGattBle2 = null;
        }
    }
    public void close(){
        close1();
        close2();
    }

    public void disconnectBLE1() {
        if (mBluetoothAdapter == null || mBluetoothGattBle1 == null) {
            Log.d("nullble1", "null ble 1 detected ");
            return;
        }else{
            Log.d("notnullble1", "ble1 is not null");
            mBluetoothGattBle1.disconnect();
            close1();
        }
        Log.d("disconnectble1", "disconnected ble 1");
    }
    public void disconnectBLE2() {
        if (mBluetoothAdapter == null || mBluetoothGattBle2 == null) {
            return;
        }else{
            mBluetoothGattBle2.disconnect();
            close2();
        }
    }
    public void disconnect(){
        disconnectBLE1();
        disconnectBLE2();
    }
    public void showMessage(String msg) {
        Log.e(TAG, msg);
    }
}