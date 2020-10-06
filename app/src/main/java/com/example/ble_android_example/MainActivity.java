package com.example.ble_android_example;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MAIN";

    private BluetoothAdapter mBluetoothAdatper;
    private BluetoothLeScanner mBluetoothLeScanner;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT=1;
    private static final long SCAN_PERIOD = 100000;

    private String mDeviceName;
    private String mDeviceAddress;

    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;

    //송수신 특성
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;

    TextView txt_ble_state, txt_ble_data;
    Button btn_data_send;

    //MainActivity가 시작되면 BluetoothLeService에 연결을 위해 ServiceConnection 인터페이스를 구현하는 객체를 생성
    //블뤁투스 서비스 클래스 안에 connect 메소드의 인자로 선택한 디바이스의 주소를 넘겨준다.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if(!mBluetoothLeService.initialize()) {
                Logs.e(TAG,"Unable to initialize Bluetooth");
                finish();
            }

            //스캔을 통해 넘겨받은 블루투스 주소를 블루투스 서비스에 넘겨줘 연결을 시작한다.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBluetoothLeService = null;
        }
    };

    //BluetoothLeService의 bradcastUpdate 메소드로부터 전달받은 intent객체를 통해
    //블루투스의 연결상태와 블루투스에서 제공하는 서비스 그리고 수신받은 데이터를 넘겨받는다.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            final String action = intent.getAction();

            if(BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                txt_ble_state.setText("connect");
                Toast.makeText(context, mDeviceName+" connected", Toast.LENGTH_SHORT).show();

            } else if(BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                Toast.makeText(context, "Bluetooth disconnected!", Toast.LENGTH_SHORT).show();

            } else if(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                findGattServices(mBluetoothLeService.getSupportedGattServices());

            } else if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txt_ble_state = (TextView)findViewById(R.id.txt_ble_state);
        txt_ble_data = (TextView)findViewById(R.id.txt_ble_data);
        btn_data_send = (Button)findViewById(R.id.btn_data_send);

        mHandler = new Handler();

        //블루투스 기능을 지원하는지 체크
        if(!this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported,Toast.LENGTH_SHORT).show();
            finish();
        }

        checkPermissions(MainActivity.this, this);

        //블루투스 어댑터 설정
        final BluetoothManager bluetoothManager= (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdatper=bluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdatper.getBluetoothLeScanner();


        if(mBluetoothAdatper==null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported,Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if(mBluetoothAdatper == null || !mBluetoothAdatper.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }

        scanLeDevice(true);

        //서비스 시작
        //클라이언트-서버 와 같이 동작. 서비스(BluetoothLeService.class)가 서버 역할을 수행
        //Activity는 BluetoothLeService에 요청을 할 수 있고, 어떠한 결과를 받을 수 있음
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        final byte[] byteSendData = new byte[]{0x4B,0x01,0x4E};

        btn_data_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                makeChange(byteSendData);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if(mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Logs.d(TAG, "Connect request reuslt = " + result);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanLeDevice(false);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    //사용자에게 권한 허용에 대해 물어봄
    public static void checkPermissions(Activity activity, Context context) {
        int PERMISSION_ALL =1;
        String[] PERMISSIONS = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_PRIVILEGED
        };

        if(!hasPermissions(context, PERMISSIONS)) {
            ActivityCompat.requestPermissions(activity, PERMISSIONS, PERMISSION_ALL);
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if(context != null && permissions != null) {
            for(String permission: permissions) {
                if(ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    //BLE 디바이스 스캔
    private void scanLeDevice(final boolean enable) {
        if(enable) {
            //별도 종료시켜주는 방법이 없기 때문에 일정시간 경과 후 종료
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothLeScanner.stopScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothLeScanner.startScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothLeScanner.stopScan(mLeScanCallback);
        }
    }

    ScanCallback mLeScanCallback = new ScanCallback() {

        String deviceName;
        String deviceAddress;

        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            BluetoothDevice device = result.getDevice();
            deviceName = device.getName();
            deviceAddress = device.getAddress();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    //콜백메소드를 통해 받은 결과값의 디바이스 이름이 비교문 안에 이름과 똑같다면
                    //이름과 주소를 변수에 할당
                    if("SSONG".equals(deviceName)) {
                        mDeviceName=deviceName;
                        mDeviceAddress=deviceAddress;

                        //위에서 주소를 넘겨줘서 connect 되기 때문에 스캔을 하고있다면 중지
                        if(mScanning) {
                            mBluetoothLeScanner.stopScan(mLeScanCallback);
                            mScanning = false;
                        }
                    }
                    else {
                        Logs.d("onConnectFailed");
                    }
                }
            });
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Logs.d("onScanFailed(", errorCode + "");
        }
    };

    //블루투스에서 지원하는 서비스 값을 돌며 송수신 특성을 찾는다.
    private void findGattServices(List<BluetoothGattService> gattServices) {
        for(BluetoothGattService gattService: gattServices) {
            characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
            characteristicRX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
        }
    }

    //data를 write 해주는 특성을 통해 data를 보내준다.
    private void makeChange(byte[] out) {
        if(mConnected) {
            characteristicTX.setValue(out);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
            mBluetoothLeService.setCharacteristicNotification(characteristicRX, true);
        }
    }


    private void displayData(final String data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txt_ble_data.setText(data);
            }
        });
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

}