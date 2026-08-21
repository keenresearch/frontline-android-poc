package com.keenresearch.keenasr_frontline_poc.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Bluetooth status listener; registers its BroadcastReceiver dynamically via register(),
 * do not declare it in the manifest (it is not a BroadcastReceiver subclass)
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT permission is declared in manifest; runtime check not required for minSdk 27
public class BluetoothStatusReceiver {
    private final String TAG = BluetoothStatusReceiver.class.getSimpleName();
    private final BluetoothHeadphoneListenerCallback listener;
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final AudioManager audioManager;
    private int scoConnectionState;
    private boolean headsetConnected = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int scoRetryCount = 0;
    private static final int MAX_SCO_RETRIES = 10;
    private static final long SCO_RETRY_DELAY_MS = 300;

    public BluetoothStatusReceiver(Context context, BluetoothHeadphoneListenerCallback callback) {
        this.context = context;
        this.listener = callback;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));

    }

    public void register() {
        startListeningForBluetoothChanges();
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            checkForConnectionUponStart();
        } else {
            // Bluetooth is not available or not enabled
            listener.bluetoothDisabled();
        }
    }

    private void checkForConnectionUponStart() {
        // Check if any audio/video devices are already connected
        boolean deviceAlreadyConnected = bluetoothAdapter.isEnabled()
                && bluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothAdapter.STATE_CONNECTED;

        if (deviceAlreadyConnected) {
            headsetConnected = true;
            listener.onDeviceConnected();
        } else {
            headsetConnected = false;
            listener.noDeviceConnected();
        }
    }

    private void startListeningForBluetoothChanges() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(receiver, filter);
    }

    public void unregister() {
        context.unregisterReceiver(receiver);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action)) {
                scoConnectionState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, 0);
                if (scoConnectionState == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    Log.d(TAG, "SCO state: CONNECTED");
                    scoRetryCount = 0;
                    listener.onAudioRouted();
                } else if (scoConnectionState == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                    Log.d(TAG, "SCO state: DISCONNECTED (retry " + scoRetryCount + "/" + MAX_SCO_RETRIES + ")");
                    if (scoRetryCount > 0 && scoRetryCount < MAX_SCO_RETRIES) {
                        // Retry SCO connection after a delay
                        handler.postDelayed(() -> {
                            if (scoConnectionState != AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                                Log.d(TAG, "Retrying SCO connection...");
                                retryScoConnection();
                            }
                        }, SCO_RETRY_DELAY_MS);
                    } else if (scoRetryCount >= MAX_SCO_RETRIES) {
                        Log.e(TAG, "SCO connection failed after " + MAX_SCO_RETRIES + " retries");
                        scoRetryCount = 0;
                        listener.onAudioRoutingFailed();
                    }
                } else if (scoConnectionState == AudioManager.SCO_AUDIO_STATE_CONNECTING) {
                    Log.d(TAG, "SCO state: CONNECTING");
                } else if (scoConnectionState == AudioManager.SCO_AUDIO_STATE_ERROR) {
                    Log.e(TAG, "SCO state: ERROR");
                    scoRetryCount = 0;
                } else {
                    Log.d(TAG, "SCO state: " + scoConnectionState);
                }
            }
            if (action != null && action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    listener.bluetoothDisabled();
                } else if (state == BluetoothAdapter.STATE_ON) {
                    listener.bluetoothEnabled();
                }
            }
            if (action != null && action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                    headsetConnected = true;
                    listener.onDeviceConnected();
                }
            } else if (action != null && action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                    headsetConnected = false;
                    scoRetryCount = 0; // Stop any pending retries
                    listener.noDeviceConnected();
                }
            }
        }
    };





    public void routeAudioToBluetoothHeadset() {
        if (!headsetConnected) {
            Log.d(TAG, "No headset connected, skipping SCO");
            return;
        }
        if (bluetoothAdapter == null) {
            Log.d(TAG, "Bluetooth adapter is null");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is not enabled");
            return;
        }
        if (audioManager == null) {
            Log.d(TAG, "Audio manager is null");
            return;
        }
        if (!audioManager.isBluetoothScoAvailableOffCall()) {
            Log.d(TAG, "Bluetooth SCO not available off call");
            return;
        }

        if (scoConnectionState == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
            Log.d(TAG, "SCO already connected");
            return;
        }

        Log.d(TAG, "Starting SCO connection (current state: " + scoConnectionState + ", isBluetoothScoOn: " + audioManager.isBluetoothScoOn() + ")");
        scoRetryCount = 1;
        audioManager.setBluetoothScoOn(true);
        audioManager.startBluetoothSco();
        // BroadcastReceiver will handle retries and call listener.onAudioRouted() when connected
    }

    private void retryScoConnection() {
        if (audioManager != null && bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            scoRetryCount++;
            audioManager.setBluetoothScoOn(true);
            audioManager.startBluetoothSco();
        }
    }



    public interface BluetoothHeadphoneListenerCallback {
        void onDeviceConnected();

        void noDeviceConnected();

        void onAudioRouted();

        void onAudioRoutingFailed();

        void bluetoothEnabled();

        void bluetoothDisabled();
    }

}

