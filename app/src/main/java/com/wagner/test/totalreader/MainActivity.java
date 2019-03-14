package com.wagner.test.totalreader;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_OPEN_GPS = 1;
    private static final int REQUEST_CODE_PERMISSION_LOCATION = 2;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mLeScanner;

    private String mReaderAddress = null;
    private BluetoothDevice mCurrentDevice = null;
    private byte[] mRaw = null;
    private String sensorSN = null;
    private boolean needTimeAlert = false;
    private boolean needCheckWrite = false;
    private boolean needNewColor = false;


    private BluetoothGatt mBluetoothGatt;

    private int delta = 2;

    public final static int FILTER_TOTALREADER = 0;
    public final static int FILTER_DATAGRABBER = 1;
    public final static int FILTER_MINI = 2;

    private int filter = FILTER_TOTALREADER;

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
//            Log.e(TAG, "onScanResult");

            byte[] adv = result.getScanRecord().getBytes();

//            Log.e(TAG, "device " + result.getDevice().getName());
//            Log.e(TAG, "adv:" + HexUtil.formatHexString(adv));

//            String name = result.getDevice().getName();
//            if (name != null && name.contains("Wagner")) {
//                mReaderAddress = result.getDevice().getAddress();
//            }

            final byte[] raw = getManufacturerSpecificData(adv);
            final String mac = result.getDevice().getAddress();

            if (raw == null) {
                return;
            }

            appendLog("+onScanResult+\nmac: " + mac + "\nraw: " + HexUtil.formatHexString(raw));

            onNewReadings(mac, raw);

//                Log.e(TAG, "raw" + HexUtil.formatHexString(raw));

//                mBluetoothGatt = mCurrentDevice.connectGatt(MainActivity.this, false, new BluetoothGattCallback() {
//                    @Override
//                    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//                        super.onConnectionStateChange(gatt, status, newState);
//                        Log.e(TAG, "onConnectionStateChange");
//                    }
//
//                    @Override
//                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
//                        super.onServicesDiscovered(gatt, status);
//                        Log.e(TAG, "onConnectionStateChange");
//                        mBluetoothGatt.disconnect();
//                    }
//                });

//                Log.e(TAG, "manufacturer data: " + HexUtil.formatHexString(raw));
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.e(TAG, "onBatchScanResults");
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "onScanFailed");
        }
    };

    public static final int LOOP_INTERVAL = 500;
    Timer parseTimer = null;
    TimerTask parseLoop = new TimerTask() {
        @Override
        public void run() {
            appendLog("+PARSE LOOP+");
            if (mCurrentDevice == null) {
                appendLog("++No reader++");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        clearReadings();
                    }
                });
            }
            else {
                appendLog("++Reader " + mReaderAddress + "++");
                if (mReaderAddress == null) {
                    appendLog("++Set Reader Address++");
                    mReaderAddress = mCurrentDevice.getAddress();
                }
                else {
                    if (!mReaderAddress.equalsIgnoreCase(mCurrentDevice.getAddress())) {
                        appendLog("++Reader Changed++");
                    }
                }
                final byte[] raw = mRaw;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        parseParameters(raw, mReaderAddress);
                    }
                });
            }

            mCurrentDevice = null;
        }
    };

    public static final int TIMEOUT_DELAY = 3000;
    Timer timeOut = null;

    private void stopTimeOut() {
        if (timeOut != null) {
            timeOut.cancel();
            timeOut.purge();
        }

        timeOut = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = (Button) findViewById(R.id.buttonSetTime);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSetTime();
            }
        });

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        checkPermissions();

        clearReadings();

//        parseTimer = new Timer();
//        parseTimer.schedule(parseLoop, 0, LOOP_INTERVAL);
    }

    private void onNewReadings(String address, final byte[] raw) {
        // filter
        switch (raw[0]) {
            case (byte) 0xBA:
                if (filter != FILTER_TOTALREADER) return;
                break;
            case (byte) 0xBC:
                if (filter != FILTER_DATAGRABBER) return;
                break;
            case (byte) 0xBD:
                if (filter != FILTER_MINI) return;
                break;
            default:
                return;
        }

        stopTimeOut();

        appendLog("++Reader " + address + "++");
        if (mReaderAddress == null) {
            appendLog("++Set Reader Address++");
            mReaderAddress = address;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    parseParameters(raw, mReaderAddress);
                }
            });


        }
        else {
            if (!mReaderAddress.equalsIgnoreCase(address)) {
                appendLog("++Reader Changed++");
                mReaderAddress = address;
            }
        }

        timeOut = new Timer();
        timeOut.schedule(new TimerTask() {
            @Override
            public void run() {
                appendLog("+TIME OUT+");
                mReaderAddress = null;
                needTimeAlert = true;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        clearReadings();
                    }
                });
            }
        }, TIMEOUT_DELAY);
    }

    private void startScan() {
        Log.e(TAG, "startScan");
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        ScanFilter.Builder filterBuilder = new ScanFilter.Builder();
        filterBuilder.setManufacturerData(0x3390, null);

        ScanSettings.Builder settingBuilder = new ScanSettings.Builder();
        settingBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);

        mLeScanner.startScan(null, settingBuilder.build(), mScanCallback);
    }

    private byte[] getManufacturerSpecificData(byte[] scanRecord) {
        try {
            ByteArrayOutputStream buff = new ByteArrayOutputStream();
            int count = scanRecord.length;
            int i = 0;
            int len;

            int type;
            int comId;
            while (i < count) {
                len = (int) scanRecord[i++] & 0xFF;
                if (len == 0) {
                    break;
                }

                type = (int) scanRecord[i] & 0xFF;
                if (type == 0xFF) {
                    if (((scanRecord[i + 1] & 0xFF) == 0x90) && ((scanRecord[i + 2] & 0xFF) == 0x33)) {
                        buff.write(scanRecord, i + 3, len - 1 - 2);
                    }
                }
                i += len;
            }

            byte[] result = buff.toByteArray();
            if (result.length == 0)
                return null;
//            result = HexUtil.hexStringToBytes("BA02E7882F5C1B01B50203563B38FFFFE7882F5C64E7882F5C1301B902322E332E3934433037394235374134333604");
            return result;
        }
        catch (IndexOutOfBoundsException e) {
            return null;
        }

    }

    private static final Interpolator ACCELERATE_INTERPOLATOR = new AccelerateInterpolator();
    private static final Interpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator();

    private void animateShutter() {

        View shutter = findViewById(R.id.mShutter);
        shutter.setVisibility(View.VISIBLE);
        shutter.setAlpha(0.f);

        ObjectAnimator alphaInAnim = ObjectAnimator.ofFloat(shutter, "alpha", 0f, 0.8f);
        alphaInAnim.setDuration(100);
        alphaInAnim.setStartDelay(100);
        alphaInAnim.setInterpolator(ACCELERATE_INTERPOLATOR);

        ObjectAnimator alphaOutAnim = ObjectAnimator.ofFloat(shutter, "alpha", 0.8f, 0f);
        alphaOutAnim.setDuration(200);
        alphaOutAnim.setInterpolator(DECELERATE_INTERPOLATOR);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playSequentially(alphaInAnim, alphaOutAnim);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                View shutter = findViewById(R.id.mShutter);
                shutter.setVisibility(View.GONE);
            }
        });
        animatorSet.start();
    }


    private void parseParameters(final byte[] raw, String mac) {

//        animateShutter();

        TextView textView;
        //•  Device type
        String deviceType = "Unknown";
        switch (raw[0]) {
            case (byte) 0xBA:
                deviceType = "Reader";
                break;
            case (byte) 0xBC:
                deviceType = "Grabber";
                break;
            case (byte) 0xBD:
                deviceType = "Mini";
                break;
        }
        textView = (TextView) findViewById(R.id.textView8);
        textView.setText(deviceType);

        Log.e(TAG, "raw:" + HexUtil.formatHexString(raw));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView textView = (TextView) findViewById(R.id.textViewRaw);
                textView.setText("RAW:" + HexUtil.formatHexString(raw));
            }
        });

//•  Sensor serial number
        byte[] snraw = Arrays.copyOfRange(raw, 10, 16);

        String sn = HexUtil.formatHexString(snraw);
        textView = (TextView) findViewById(R.id.textView5);
        textView.setText(sn.toUpperCase());

        if (!sn.equalsIgnoreCase(sensorSN)) {
            sensorSN = sn;
            sensorChanged();
        }

        float externalRH = (float)((int)(raw[6] & 0xFF) + (((int)raw[7] & 0xFF) << 8)) / 10.0f;
        float externalTemp = (float)((int)(raw[8] & 0xFF) + (((int)raw[9] & 0xFF) << 8)) / 10.0f;

        textView = (TextView) findViewById(R.id.tvRH2);
        textView.setText(externalRH + "%");
        textView = (TextView) findViewById(R.id.tvTEMP2);
        textView.setText(externalTemp + "°F");

//•  Ambient reading (RH/Temp)
        float ambientRH = (float)((int)(raw[25] & 0xFF) + (((int)raw[26] & 0xFF) << 8)) / 10.0f;
        float ambientTemp = (float)((int)(raw[27] & 0xFF) + (((int)raw[28] & 0xFF) << 8)) / 10.0f;

        textView = (TextView) findViewById(R.id.tvRH1);
        textView.setText(ambientRH + "%");
        textView = (TextView) findViewById(R.id.tvTEMP1);
        textView.setText(ambientTemp + "°F");

//•  Device date time(hh:mm:ss)
        long timestamp = (int)(raw[16] & 0xFF) + (((int)raw[17] & 0xFF) << 8) + (((int)raw[18] & 0xFF) << 16) + (((int)raw[19] & 0xFF) << 24);
        timestamp *= 1000L;
        Date date = new Date(timestamp);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        SimpleDateFormat sdf2 = new SimpleDateFormat("MMM d, yyyy");
        sdf.setTimeZone(TimeZone.getDefault());
        sdf.setTimeZone(TimeZone.getDefault());

        String formattedTime = sdf.format(date);
        textView = (TextView) findViewById(R.id.textView2);
        textView.setText(formattedTime);

        formattedTime = sdf2.format(date);
        textView = (TextView) findViewById(R.id.tvDeviceDate);
        textView.setText(formattedTime);

//•  Phone date time (hh:mm:ss)
        date = new Date();
        formattedTime = sdf.format(date);
        textView = (TextView) findViewById(R.id.textView3);
        textView.setText(formattedTime);

        formattedTime = sdf2.format(date);
        textView = (TextView) findViewById(R.id.tvPhoneDate);
        textView.setText(formattedTime);

        long diff = Math.abs(date.getTime() - timestamp);

        appendLog("time diff :" + date.getTime() + "-" + timestamp + " delta " + delta);
        if (diff > (delta * 1000l)) {
            if (needCheckWrite) {
                WagnerToast.showError(this, "Device time set failed", Toast.LENGTH_LONG);
                appendLog("Device time set failed");
                needCheckWrite = false;
            }
            else if (needTimeAlert) {
                WagnerToast.showError(this, R.string.wrong_time_message);
                appendLog("Device time wrong");
                needTimeAlert = false;
            }
        }
        else {
            if (needCheckWrite) {
                needNewColor = true;
                WagnerToast.showSuccess(this, "Device time set successfully", Toast.LENGTH_LONG);
                appendLog("Device time set successfully");
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        needNewColor = false;
                    }
                }, 3000);
                needCheckWrite = false;
            }
        }

//•  Battery level
        int battery = raw[20];
        textView = (TextView) findViewById(R.id.textView4);
        textView.setText(battery + "%");
//•  Device mac address
        textView = (TextView) findViewById(R.id.textView6);
        textView.setText(mac);
//•  Fw version
        byte[] fwraw = Arrays.copyOfRange(raw, 29, 34);
        String fwversion = null;
        try {
            fwversion = new String(fwraw, "ASCII");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            fwversion = "0.0.0";
        }
        textView = (TextView) findViewById(R.id.textView7);
        textView.setText(fwversion);

//•  Sensor type
        String sensorType = "" + raw[1];
//        switch (raw[0]) {
//            case (byte) 0x00:
//                deviceType = "Reader";
//                break;
//            case (byte) 0xBC:
//                deviceType = "Grabber";
//                break;
//            case (byte) 0xBD:
//                deviceType = "Mini";
//                break;
//        }
        textView = (TextView) findViewById(R.id.textView9);
        textView.setText(sensorType);
//•  Device name
        byte[] nameraw = Arrays.copyOfRange(raw, 34, 46);
        String deviceName = null;
        try {
            deviceName = new String(nameraw, "ASCII");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            deviceName = "Unknown";
        }
        textView = (TextView) findViewById(R.id.textView10);
        textView.setText(deviceName);
//•  A button to set the time on the devic

    }

    private void clearReadings() {
        TextView textView;
        String none = "None";

        textView = (TextView) findViewById(R.id.textView8);
        textView.setText(none);

        textView = (TextView) findViewById(R.id.textViewRaw);
        textView.setText("RAW:");

//•  Sensor serial number
        textView = (TextView) findViewById(R.id.textView5);
        textView.setText(none);

        textView = (TextView) findViewById(R.id.tvRH2);
        textView.setText("?%");
        textView = (TextView) findViewById(R.id.tvTEMP2);
        textView.setText("?°F");

//•  Ambient reading (RH/Temp)
        textView = (TextView) findViewById(R.id.tvRH1);
        textView.setText("?%");
        textView = (TextView) findViewById(R.id.tvTEMP1);
        textView.setText("?°F");

//•  Device date time(hh:mm:ss)
        textView = (TextView) findViewById(R.id.textView2);
        textView.setText(none);

        textView = (TextView) findViewById(R.id.tvDeviceDate);
        textView.setText(none);

//•  Phone date time (hh:mm:ss)
        textView = (TextView) findViewById(R.id.textView3);
        textView.setText(none);

        textView = (TextView) findViewById(R.id.tvPhoneDate);
        textView.setText(none);

//•  Battery level
        textView = (TextView) findViewById(R.id.textView4);
        textView.setText("?%");
//•  Device mac address
        textView = (TextView) findViewById(R.id.textView6);
        textView.setText(none);
//•  Fw version
        textView = (TextView) findViewById(R.id.textView7);
        textView.setText(none);

//•  Sensor type
        textView = (TextView) findViewById(R.id.textView9);
        textView.setText(none);
//•  Device name
        textView = (TextView) findViewById(R.id.textView10);
        textView.setText(none);
//•  A button to set the time on the devic
    }

    private void sensorChanged() {
        needTimeAlert = true;
        needCheckWrite = false;
        needNewColor = false;
    }

    public void onSetTime() {

        if (mReaderAddress == null)
            return;

        Log.e(TAG, "onSetTime");
        appendLog("onSetTime");

        stopTimeOut();

        mLeScanner.stopScan(mScanCallback);


        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mReaderAddress);
        appendLog("attempt to connect device: " + mReaderAddress);
        mBluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            WagnerToast.showSuccess(getApplicationContext(), "Device connected", Toast.LENGTH_SHORT);
                            appendLog("toast Device connected.");
                        }
                    });

                    Log.i(TAG, "Connected to GATT server.");
                    Log.i(TAG, "Attempting to start service discovery:" +
                            mBluetoothGatt.discoverServices());
                    appendLog("Connected to GATT server.");

                } else {
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            WagnerToast.showError(getApplicationContext(), "Device disconnected", Toast.LENGTH_SHORT);
//                        }
//                    });

                    Log.i(TAG, "Disconnected from GATT server.");
                    appendLog("Disconnected from GATT server.");
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);
                if (status == BluetoothGatt.GATT_SUCCESS) {


                    List<BluetoothGattService> gattServices = mBluetoothGatt.getServices();
//                    Log.e("onServicesDiscovered", "Services count: "+gattServices.size());

                    for (BluetoothGattService gattService : gattServices) {
                        String serviceUUID = gattService.getUuid().toString();
                        Log.e("onServicesDiscovered", "Service uuid "+serviceUUID);
                        appendLog("onServicesDiscovered " + serviceUUID);
                        if (serviceUUID.equalsIgnoreCase("0000ffe0-0000-1000-8000-00805f9b34fb")) {
//                            runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    WagnerToast.showSuccess(getApplicationContext(), "Service discovered", Toast.LENGTH_SHORT);
//                                }
//                            });

                            for (BluetoothGattCharacteristic characteristic : gattService.getCharacteristics()) {
//                Log.e("detected characteristic uuid: ", service.getUuid().toString());
                                String characterUUID = Long.toHexString(
                                        characteristic.getUuid()
                                                .getMostSignificantBits()).substring(0, 4);

//                addText(txt, "detected characteristic uuid: " + characterUUID);
                                appendLog("detected characteristic uuid: " + characterUUID);

                                if (characterUUID.equals("ffe1")) {
//                                    runOnUiThread(new Runnable() {
//                                        @Override
//                                        public void run() {
//                                            WagnerToast.showSuccess(getApplicationContext(), "Characteristic discovered", Toast.LENGTH_SHORT);
//                                        }
//                                    });

                                    Date now = new Date();
                                    long currentTime = now.getTime();
                                    int delay = (int) (currentTime % 1000L);

                                    appendLog("get time" + currentTime);

                                    long timestamp = now.getTime() / 1000L + 1;
                                    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                                    buffer.putLong(timestamp);
                                    byte[] value = buffer.array();
                                    final byte[] raw = new byte[4];
                                    raw[0] = value[7];
                                    raw[1] = value[6];
                                    raw[2] = value[5];
                                    raw[3] = value[4];

                                    appendLog("wait time second " + delay + "timestamp" + timestamp);

//                                    Log.e(TAG, timestamp + ":" + HexUtil.formatHexString(raw));

                                    final BluetoothGattCharacteristic w_characteristic = characteristic;
                                    Timer timer = new Timer();
                                    timer.schedule(new TimerTask() {
                                        @Override
                                        public void run() {
                                            w_characteristic.setValue(raw);
                                            mBluetoothGatt.writeCharacteristic(w_characteristic);
                                            appendLog("WRITE: " + HexUtil.formatHexString(raw));

                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    TextView textView = (TextView) findViewById(R.id.textViewWrite);
                                                    textView.setText("WRITE:" + HexUtil.formatHexString(raw));
                                                    WagnerToast.showSuccess(getApplicationContext(), "New Date time set", Toast.LENGTH_LONG);
                                                    appendLog("toast New Date time set.");
                                                    needCheckWrite = true;
                                                }
                                            });
                                        }
                                    }, delay);
                                    break;
                                }
                            }
                            break;
                        }
                    }


//                    mBluetoothGatt.disconnect();
                } else {
                    Log.w(TAG, "onServicesDiscovered received: " + status);
                    appendLog("onServicesDiscovered received: " + status);
                    mBluetoothGatt.disconnect();
                }
            }
        });

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                mBluetoothGatt.disconnect();
                startScan();
                Log.e(TAG, "scan restarted");
                appendLog("restart scan");
            }
        }, 3000);


    }

    @Override
    public final void onRequestPermissionsResult(int requestCode,
                                                 @NonNull String[] permissions,
                                                 @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE_PERMISSION_LOCATION:
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            onPermissionGranted(permissions[i]);
                        }
                    }
                }
                break;
        }
    }

    private void checkPermissions() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            WagnerToast.showSuccess(this, R.string.please_open_blue);
            return;
        }

        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
        List<String> permissionDeniedList = new ArrayList<>();
        for (String permission : permissions) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, permission);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                onPermissionGranted(permission);
            } else {
                permissionDeniedList.add(permission);
            }
        }
        if (!permissionDeniedList.isEmpty()) {
            String[] deniedPermissions = permissionDeniedList.toArray(new String[permissionDeniedList.size()]);
            ActivityCompat.requestPermissions(this, deniedPermissions, REQUEST_CODE_PERMISSION_LOCATION);
        }
    }

    private void onPermissionGranted(String permission) {
        switch (permission) {
            case Manifest.permission.ACCESS_FINE_LOCATION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !checkGPSIsOpen()) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.notifyTitle)
                            .setMessage(R.string.gpsNotifyMsg)
                            .setNegativeButton(R.string.cancel,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            finish();
                                        }
                                    })
                            .setPositiveButton(R.string.setting,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                            startActivityForResult(intent, REQUEST_CODE_OPEN_GPS);
                                        }
                                    })

                            .setCancelable(false)
                            .show();
                } else {
                    startScan();
                }
                break;
        }
    }

    private boolean checkGPSIsOpen() {
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null)
            return false;
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_GPS) {
            if (checkGPSIsOpen()) {
                startScan();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.warning_time:
                showTimePicker();
                return true;
            case R.id.filter:
                showFilterPicker();
                return true;
            case R.id.clear_log:
                clearLogFile();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void showTimePicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.warning_time_delta);


        View view = LayoutInflater.from(this).inflate(R.layout.dialog_timedelta, null);
        final NumberPicker np = (NumberPicker) view.findViewById(R.id.timeDeltaPicker);
        np.setMaxValue(100); // max value 100
        np.setMinValue(0);   // min value 0
        np.setValue(delta);
        np.setWrapSelectorWheel(false);
        np.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            }
        });

        builder.setView(view);

        builder.setPositiveButton(R.string.set, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                delta = np.getValue();
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        final AlertDialog dialog = builder.create();

        dialog.show();
    }

    public void showFilterPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.filter)
                .setItems(R.array.FilterList, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        filter = which;
                    }
                });
        builder.create().show();
    }

    public void appendLog(String text)
    {
        File logFile = getLogFile();
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
                appendLog(BuildConfig.VERSION_NAME + "." + BuildConfig.VERSION_CODE);
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.append("" + System.currentTimeMillis());
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public File getLogFile() {
        return new File(Environment.getExternalStorageDirectory().getAbsoluteFile() + "/Wagner.log");
    }

    public void clearLogFile() {
        File logfile = getLogFile();
        logfile.delete();
    }
}
