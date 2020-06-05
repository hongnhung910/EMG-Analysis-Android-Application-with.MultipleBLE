package emgsignal.v3;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v7.app.ActionBarDrawerToggle;
import android.util.Log;
import android.view.MenuItem;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;

import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.File;
import java.util.ArrayList;

import emgsignal.v3.BLE.Constants;
import emgsignal.v3.BLE.DeviceListActivity;
import emgsignal.v3.Database.Add_Sensor_Activity;
import emgsignal.v3.Database.Add_User_Activity;
import emgsignal.v3.Database.DBManager;
import emgsignal.v3.Database.SensorFormat;
import emgsignal.v3.Database.UserFormat;
import emgsignal.v3.SavedDataProcessing.ExternalStorageUtil;
import emgsignal.v3.SavedDataProcessing.ListFilesActivity;
import emgsignal.v3.SavedDataProcessing.ListFolderActivity;
import emgsignal.v3.SavedDataProcessing.SaveData;
import emgsignal.v3.SignalProcessing.IIR_Filter;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;


    //private static EditText textView;
    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;   // The BluetoothAdapter is required for any and all Bluetooth activity
    private Button btnConnectDisconnect1, btnConnectDisconnect2, btnSaveData, btnReset;
    private DeviceListActivity deviceListActivity;
    byte[] txValue1, txValue2;
    int fs=1000;
    double[] emg_1 = new double[30];
    double[] emg_2 = new double[30];

    private LineGraphSeries<DataPoint> series_maternal_1;
    private LineGraphSeries<DataPoint> series_maternal_2;

    private double lastX1 = 0;
    private double lastX2 = 0;

    boolean isRunning = false;
    boolean isSaving  = false;

    ArrayList<Double> data1Save = new ArrayList();
    ArrayList<Double> data2Save = new ArrayList();

    IIR_Filter filter = new IIR_Filter();

    double[] filter_input1 =  {0, 0, 0, 0, 0, 0, 0, 0, 0,0,0};
    double[] filter_output1 = {0, 0, 0, 0, 0, 0, 0, 0,0,0};

    double[] filter_input_for_envelope =  {0, 0, 0, 0, 0};
    double[] filter_output_for_envelope = {0, 0, 0, 0};

    private SaveData saveData = new SaveData();

    private TextView timerValue;
    private Handler customHandler = new Handler();
    private long startTime = 0L;
    long timeInMilliseconds = 0L;
    long timeSwapBuff = 0L;
    long updatedTime = 0L;
    private EditText et_temp , et_humid, et_notes_1, et_notes_2;
    private ArrayList<String> listUser, listSensor;
    private int secs;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Handle menu section
        HandleMenu();

        //Create save data Folder
        CreateSaveFolder();

        deviceListActivity = new DeviceListActivity();

        initGraphMaternal1();
        initGraphMaternal2();

        btnSaveData = findViewById(R.id.btn_saveData);
        btnReset = findViewById(R.id.btn_reset);
        timerValue = findViewById(R.id.timerValue);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter(); // lay gia tri default ban dau la null

        // vơi gia tri ban dau la null, bluetooth khong hoat dong
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnConnectDisconnect1=findViewById(R.id.btn_connect_1);
        btnConnectDisconnect2=findViewById(R.id.btn_connect_2);
        service_init();

        // Handle Disconnect & Connect button
        btnConnectDisconnect1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else {

                    if (btnConnectDisconnect1.getText().equals("CN_BLE1")){
                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class); // chuyen qua device list activity
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);

                    } else {
                        //Disconnect button pressed
                        mService.disconnectBLE1();
                        btnConnectDisconnect1.setText("CN_BLE1");
                    }
                }
            }
        });

        btnConnectDisconnect2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else {

                    if (btnConnectDisconnect2.getText().equals("CN_BLE2")){
                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class); // chuyen qua device list activity
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);

                    } else {
                        //Disconnect button pressed
                        mService.disconnectBLE2();
                        btnConnectDisconnect2.setText("CN_BLE2");
                    }
                }
            }
        });

        // Handle Save emgsignal.v3.data function
        btnSaveData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(data1Save.size() == 0 && data2Save.size() == 0)
                { Toast.makeText(MainActivity.this, "No EMG signal data available yet", Toast.LENGTH_SHORT).show();}
                else {
                    if (btnSaveData.getText().equals("Save")) {
                        data1Save.clear();
                        data2Save.clear();
                        btnSaveData.setText("Saving");
                        isSaving = true;
                        startTime = SystemClock.uptimeMillis();
                        customHandler.postDelayed(updateTimerThread, 0);


                    }
                    else {
                        timeSwapBuff = 0;
                        customHandler.removeCallbacks(updateTimerThread);
                        mService.disconnect();
                        btnConnectDisconnect1.setText("CN_BLE1");
                        btnConnectDisconnect2.setText("CN_BLE2");
                        btnSaveData.setText("Save");
                        showdialog();
                    }
                }
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.disconnect();
                btnConnectDisconnect1.setText("CN_BLE1");
                btnConnectDisconnect2.setText("CN_BLE2");
                btnSaveData.setText("Save");
                resetData();
            }
        });
    }

    private void resetData(){
        isRunning = false;
        isSaving = false;
        data1Save.clear();
        data2Save.clear();
        lastX1=0;
        lastX2=0;
        series_maternal_1.resetData(new DataPoint[] {
                new DataPoint(lastX1, 0)
        });
        series_maternal_2.resetData(new DataPoint[] {
                new DataPoint(lastX2, 0)
        });
        initGraphMaternal1();
        initGraphMaternal2();
        timeSwapBuff = 0;
        customHandler.removeCallbacks(updateTimerThread);
        timerValue.setText("00 sec");
    }

    //Create graph
    private void initGraphMaternal1(){
        // we get graph view instance
        GraphView graph =  findViewById(R.id.realtime_chart_1);
        graph.setTitleColor(Color.BLUE);
        graph.setTitle("Real-time chart 1");
        series_maternal_1 = new LineGraphSeries();
        series_maternal_1.setColor(Color.RED);
        series_maternal_1.setThickness(2);
        graph.addSeries(series_maternal_1);

        Viewport viewport = graph.getViewport();
        viewport.setXAxisBoundsManual(true);
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(0);
        viewport.setMaxY(100);
        viewport.setMinX(0);
        viewport.setMaxX(10000);
        viewport.setScrollable(true);
        viewport.setScalable(true);
        graph.getGridLabelRenderer().setNumHorizontalLabels(10);
        graph.getGridLabelRenderer().setNumVerticalLabels(5);
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(true);
        graph.getGridLabelRenderer().setVerticalLabelsVisible(true);
        graph.getGridLabelRenderer().setLabelsSpace(5);
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    // show normal x values
                    return super.formatLabel(value, isValueX);
                } else {
                    // show currency for y values
                    return super.formatLabel(value, isValueX);
                }
            }
        });
    }
    private void initGraphMaternal2(){
        // we get graph view instance
        GraphView graph =  findViewById(R.id.realtime_chart_2);
        graph.setTitleColor(Color.RED);
        graph.setTitle("Real-time chart 2");
        series_maternal_2 = new LineGraphSeries();
        series_maternal_2.setColor(Color.BLUE);
        series_maternal_2.setThickness(2);
        graph.addSeries(series_maternal_2);

        Viewport viewport = graph.getViewport();
        viewport.setXAxisBoundsManual(true);
        viewport.setYAxisBoundsManual(true);
        viewport.setMinY(0);
        viewport.setMaxY(100);
        viewport.setMinX(0);
        viewport.setMaxX(10000);
        viewport.setScrollable(true);
        viewport.setScalable(true);
        graph.getGridLabelRenderer().setNumHorizontalLabels(10);
        graph.getGridLabelRenderer().setNumVerticalLabels(5);
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(true);
        graph.getGridLabelRenderer().setVerticalLabelsVisible(true);
        graph.getGridLabelRenderer().setLabelsSpace(5);
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    // show normal x values
                    return super.formatLabel(value, isValueX);
                } else {
                    // show currency for y values
                    return super.formatLabel(value, isValueX);
                }
            }
        });
    }
    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }
        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;

        }
    };
    // Ham doc du lieu
    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, final Intent intent) {
            String action = intent.getAction();

            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.i("Uart", "connected");
                        if(mService.mBluetoothGattBle1 != null){
                            btnConnectDisconnect1.setText("DCN_BLE1");
                        }
                        if(mService.mBluetoothGattBle2 !=null){
                            btnConnectDisconnect2.setText("DCN_BLE2");
                        }
                        if(!isSaving) {Toast.makeText(getApplicationContext(), "Connected-ble", Toast.LENGTH_LONG).show();}
                        mState = UART_PROFILE_CONNECTED;
                    }
                });
            }


            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Log.i("Uart", "disconnected");
                        if(!isSaving) {Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_LONG).show();}
                        if(mService.mBluetoothGattBle1 == null && mService.mBluetoothGattBle2 == null){
                            mService.close();
                            mState = UART_PROFILE_DISCONNECTED;
                            isRunning = false;
                        }

                    }
                });
            }

            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {

                if(intent.getByteArrayExtra(UartService.EXTRA_DATA_BLE1) !=null){
                    txValue1 = intent.getByteArrayExtra(UartService.EXTRA_DATA_BLE1);
                }
                if(intent.getByteArrayExtra(UartService.EXTRA_DATA_BLE2) !=null){
                    txValue2 = intent.getByteArrayExtra(UartService.EXTRA_DATA_BLE2);
                }

                // firstDataBuffer = new double[3000];

                if(txValue1 != null){
                    for (int i = 0; i < 10; i++) {
                        emg_1[i] = (txValue1[i*4+2]&0xff&0x3f) + (txValue1[i*4+3]&0xff&0x3f)*64;
                        data1Save.add(emg_1[i]);
                        lastX1=lastX1 + 1/fs;
                        series_maternal_1.appendData(new DataPoint(lastX1,emg_1[i]), true, 10000);
                        Log.d(TAG, lastX1++ + ", " + emg_1[i]);
                    }
                }
                if(txValue2 != null){
                    for (int i = 0; i < 10; i++) {
                        emg_2[i] = (txValue2[i*4+2]&0xff&0x3f) + (txValue2[i*4+3]&0xff&0x3f)*64;
                        data2Save.add(emg_2[i]);
                        lastX2=lastX2 + 1/fs;
                        series_maternal_2.appendData(new DataPoint(lastX2,emg_2[i]), true, 10000);
                        Log.d(TAG, lastX2++ + ", " + emg_2[i]);
                    }
                }
            }
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                if(!isSaving) {
                    showMessage("Device doesn't support UART. Disconnecting");
                    mService.disconnect();
                }
            }
        }
    };
    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
        Log.i(TAG, "service_init: here............");
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }


    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService= null;

    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }



    @Override
    protected void onResume() {
        super.onResume();

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        } else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    Constants.REQUEST_LOCATION_ENABLE_CODE);
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Your devices that don't support BLE", Toast.LENGTH_LONG).show();
            finish();
        }
        if (!mBtAdapter.enable()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, Constants.REQUEST_BLUETOOTH_ENABLE_CODE);
        }
        registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
        if (mService != null) {
            // final boolean result = mService.connect(mDevice.getAddress());
            //  Log.d(TAG, "Connect request result=" + result);
        }
    }
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
            {
                if (resultCode == Activity.RESULT_OK && data != null) {

                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress);

                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    mService.connect(deviceAddress);
                    Toast.makeText(MainActivity.this,"Connected",Toast.LENGTH_SHORT).show();
                    Log.d(TAG,"Connect Success");
                    //resetData();
                    isRunning = true;
                }
                if (resultCode == Activity.RESULT_CANCELED)
                {
                    resetData();
                    Toast.makeText(MainActivity.this,"No device choosen",Toast.LENGTH_SHORT).show();
                }
            }
            break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
            {

                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    //finish();
                }
                if (resultCode == Activity.RESULT_CANCELED)
                    Toast.makeText(MainActivity.this,"No device choosen",Toast.LENGTH_SHORT).show();
            }
            break;

            //case CREATE_REQUEST_CODE:
            default:

                Log.e(TAG, "wrong request code");
                break;
        }
    }


    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onBackPressed() {
        if (mState == UART_PROFILE_CONNECTED) {
            Intent startMain = new Intent(Intent.ACTION_MAIN);
            startMain.addCategory(Intent.CATEGORY_HOME);
            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startMain);
            showMessage("nRFUART's running in background.\n             Disconnect to exit");
        }
        else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.popup_title1)
                    .setMessage(R.string.popup_message)
                    .setNegativeButton(R.string.popup_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setPositiveButton(R.string.popup_no, null)
                    .show();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    public void HandleMenu(){
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
    }
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_home:
                break;
            case R.id.menu_saved_data:
                Intent intent = new Intent(MainActivity.this, ListFolderActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_add_user:
                Intent intent2 = new Intent(MainActivity.this, Add_User_Activity.class);
                startActivity(intent2);
                break;
            case R.id.menu_add_sensor:
                Intent intent3 = new Intent(MainActivity.this, Add_Sensor_Activity.class);
                startActivity(intent3);
                break;
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    private Runnable updateTimerThread = new Runnable() {

        public void run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            updatedTime = timeSwapBuff + timeInMilliseconds;
            secs = (int) (updatedTime / 1000);
            secs = secs % 60;
            timerValue.setText(String.format("%02d", secs) +" sec");
            customHandler.postDelayed(this, 0);
        }

    };

    private void CreateSaveFolder() {
        try {
            if (ExternalStorageUtil.isExternalStorageMounted()) {
                // Check whether this app has write external storage permission or not.
                int writeExternalStoragePermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                // If do not grant write external storage permission.
                if (writeExternalStoragePermission != PackageManager.PERMISSION_GRANTED) {
                    // Request user to grant write external storage permission.
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                }
                else {
                    File sdCard = Environment.getExternalStorageDirectory();
                    if (sdCard.exists()) {
                        File publicDcimDirPath = new File(sdCard.getAbsolutePath() + "/EMG_Data");

                        if (!publicDcimDirPath.exists()) {
                            publicDcimDirPath.mkdirs();
                            Log.i("making", "Creating Directory: " + publicDcimDirPath);
                        }

                    }
                }
            }
        }
        catch (Exception ex)
        {
            Log.e("EXTERNAL_STORAGE", ex.getMessage(), ex);
        }
    }

    public void showdialog() {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_info_saved);
        Window window = dialog.getWindow();
        window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        final String addUser = "Add user info before saving data";
        final String addSensor = "Add sensor info before saving data";
        final DBManager dbManager = new DBManager(MainActivity.this);
        listUser = new ArrayList<>();
        listUser.add("Select testee");
        listSensor = new ArrayList<>();
        listSensor.add("Select sensor");
        ArrayList<String> getUsersId = new ArrayList<>();
        ArrayList<String> getTypeSensor = new ArrayList<>();
        getUsersId = dbManager.getAllUsersId();
        getTypeSensor = dbManager.getAllSensorType();
        if (getUsersId.isEmpty()) {
           listUser.add(addUser);
        } else {
            for (int i = 0; i < dbManager.NumberOfUsers(); i++) {
                listUser.add(getUsersId.get(i));
            }}
        if (getTypeSensor.isEmpty()) {
            listSensor.add(addSensor);
        } else {
            for (int j = 0; j < dbManager.NumberOfSensors(); j++) {
                listSensor.add(getTypeSensor.get(j));
            }
        }


        //Spinner setup for selecting testee_1
            final Spinner spinner_testee1 = dialog.findViewById(R.id.spinner_testee1);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                    this,
                    R.layout.custom_spinner,
                    listUser
            ) {
                @Override
                public boolean isEnabled(int position){
                    if(position == 0) { return false; }
                    else { return true; }
                }
                @Override
                public View getDropDownView(int position, View convertView,
                                            ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) view;
                    if(position == 0){
                        // Set the hint text color gray
                        tv.setTextColor(Color.GRAY);
                    }
                    else { tv.setTextColor(Color.BLACK); }
                    return view;
                }
            };
            adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown);
            spinner_testee1.setAdapter(adapter);
            spinner_testee1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                    String selectedUser_1 = spinner_testee1.getItemAtPosition(position).toString();
                    if (selectedUser_1.equals(addUser)) {
                        Intent intentAddUser = new Intent(MainActivity.this, Add_User_Activity.class);
                        startActivity(intentAddUser);
                        dialog.dismiss();
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });


        //Spinner setup for selecting testee_2
            final Spinner spinner_testee2 = dialog.findViewById(R.id.spinner_testee2);
            ArrayAdapter<String> adapter2  = new ArrayAdapter<String>(
                    this,
                    R.layout.custom_spinner,
                    listUser
            ) {
                @Override
                public boolean isEnabled(int position){
                    if(position == 0) { return false; }
                    else { return true; }
                }
                @Override
                public View getDropDownView(int position, View convertView,
                                            ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) view;
                    if(position == 0){
                        // Set the hint text color gray
                        tv.setTextColor(Color.GRAY);
                    }
                    else { tv.setTextColor(Color.BLACK); }
                    return view;
                }
            };
            adapter2.setDropDownViewResource(R.layout.custom_spinner_dropdown);
            spinner_testee2.setAdapter(adapter2);
            spinner_testee2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                    String selectedUser_2 = spinner_testee2.getItemAtPosition(position).toString();
                    if (selectedUser_2.equals(addUser)) {
                        Intent intentAddSensor = new Intent(MainActivity.this, Add_User_Activity.class);
                        startActivity(intentAddSensor);
                        dialog.dismiss();
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });

        //Spinner setup for selecting sensor_1
        final Spinner spinner_sensor1 = dialog.findViewById(R.id.spinner_sensor_1);
        ArrayAdapter<String> adapter3  = new ArrayAdapter<String>(
                this,
                R.layout.custom_spinner,
                listSensor
        ) {
            @Override
            public boolean isEnabled(int position){
                if(position == 0) { return false; }
                else { return true; }
            }
            @Override
            public View getDropDownView(int position, View convertView,
                                        ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                if(position == 0){
                    // Set the hint text color gray
                    tv.setTextColor(Color.GRAY);
                }
                else { tv.setTextColor(Color.BLACK); }
                return view;
            }
        };
        adapter3.setDropDownViewResource(R.layout.custom_spinner_dropdown);
        spinner_sensor1.setAdapter(adapter3);
        spinner_sensor1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                String selectedSensor_1 = spinner_sensor1.getItemAtPosition(position).toString();
                if (selectedSensor_1.equals(addSensor)) {
                    Intent intentAddSensor = new Intent(MainActivity.this, Add_User_Activity.class);
                    startActivity(intentAddSensor);
                    dialog.dismiss();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        //Spinner setup for selecting sensor_2
        final Spinner spinner_sensor2 = dialog.findViewById(R.id.spinner_sensor_2);
        ArrayAdapter<String> adapter4  = new ArrayAdapter<String>(
                this,
                R.layout.custom_spinner,
                listSensor
        ) {
            @Override
            public boolean isEnabled(int position){
                if(position == 0) { return false; }
                else { return true; }
            }
            @Override
            public View getDropDownView(int position, View convertView,
                                        ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                if(position == 0){
                    // Set the hint text color gray
                    tv.setTextColor(Color.GRAY);
                }
                else { tv.setTextColor(Color.BLACK); }
                return view;
            }
        };
        adapter4.setDropDownViewResource(R.layout.custom_spinner_dropdown);
        spinner_sensor2.setAdapter(adapter4);
        spinner_sensor2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                String selectedSensor_2 = spinner_sensor2.getItemAtPosition(position).toString();
                if (selectedSensor_2.equals(addSensor)) {
                    Intent intentAddSensor = new Intent(MainActivity.this, Add_User_Activity.class);
                    startActivity(intentAddSensor);
                    dialog.dismiss();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        Button btnSave = dialog.findViewById(R.id.Dialog_btnSave);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String selectedUser_1 = spinner_testee1.getSelectedItem().toString().trim();
                String selectedUser_2 = spinner_testee2.getSelectedItem().toString().trim();

                String selectedSensor_1 = spinner_sensor1.getSelectedItem().toString().trim();
                String selectedSensor_2 = spinner_sensor2.getSelectedItem().toString().trim();

                et_temp = dialog.findViewById(R.id.temp);
                et_humid = dialog.findViewById(R.id.humid);
                et_notes_1 = dialog.findViewById(R.id.etNotes_1);
                et_notes_2 = dialog.findViewById(R.id.etNotes_2);

                String temp = et_temp.getText().toString().trim();
                String humid = et_humid.getText().toString().trim();
                String notes_1 = et_notes_1.getText().toString().trim();
                String notes_2 = et_notes_2.getText().toString().trim();

                //character that not allowed in filenames https://stackoverflow.com/questions/1976007/what-characters-are-forbidden-in-windows-and-linux-directory-names
                char[] chars_note = notes_1.toCharArray();
                char[] chars_note2 = new char[chars_note.length];
                int a = 0, b = 0;
                while(a < chars_note.length)
                {
                    if(chars_note[a] != '*' && chars_note[a] != '/')
                    {
                        chars_note2[b++] = chars_note[a];
                    } else chars_note2[b++] = ' ';
                    a++;
                }
                notes_1 = String.valueOf(chars_note2);
                notes_1.replaceAll("\\s+", "");


                char[] chars_note3 = notes_2.toCharArray();
                char[] chars_note4 = new char[chars_note3.length];
                int a2 = 0, b2 = 0;
                while(a2 < chars_note3.length)
                {
                    if(chars_note3[a2] != '*' && chars_note3[a2] != '/')
                    {
                        chars_note4[b2++] = chars_note3[a2];
                    } else chars_note4[b2++] = ' ';
                    a2++;
                }
                notes_2 = String.valueOf(chars_note4);
                notes_2.replaceAll("\\s+", "");


                if((!selectedUser_1.equals("Select testee")) &&
                        (!selectedUser_2.equals("Select testee")) &&
                        (!selectedSensor_1.equals("Select sensor")) &&
                        (!selectedSensor_2.equals("Select sensor")) &&
                        (!temp.equals("")) &&
                        (!humid.equals(""))){
                    UserFormat selectedUserObject_1 = dbManager.getUser(selectedUser_1);
                    UserFormat selectedUserObject_2 = dbManager.getUser(selectedUser_2);

                    SensorFormat selectedSensorObject_1 = dbManager.getSensor(selectedSensor_1);
                    SensorFormat selectedSensorObject_2 = dbManager.getSensor(selectedSensor_2);

                    saveData.save(data1Save, selectedUser_1 , selectedSensor_1 ,
                            selectedUserObject_1.getHeight()+"cm, "+selectedUserObject_1.getWeight()+"kg, R(body) = "+selectedUserObject_1.getBody_res()+"KOhm",
                            "M= " + selectedSensorObject_1.getResMid()+", E= " + selectedSensorObject_1.getResEnd()+", R= "+selectedSensorObject_1.getResRef()+"KOhm",
                            "Temperature: " + temp + "°C, RH: " + humid + "%", notes_1 );
                    saveData.save(data2Save, selectedUser_2 , selectedSensor_2 ,
                            selectedUserObject_2.getHeight()+"cm, "+selectedUserObject_2.getWeight()+"kg, R(body) = "+selectedUserObject_2.getBody_res()+"KOhm",
                            "M= " + selectedSensorObject_2.getResMid()+", E= " + selectedSensorObject_2.getResEnd()+", R= "+selectedSensorObject_2.getResRef()+"KOhm",
                            "Temperature: " + temp + "°C, RH: " + humid + "%", notes_2 );

                    Toast.makeText(MainActivity.this, "Data saved successfully",Toast.LENGTH_SHORT).show();
                    resetData();
                    dialog.dismiss();
                }
                else{
                    Toast addFailed = Toast.makeText(getApplicationContext(), "All the fields must be filled and contain no invalid characters" , Toast.LENGTH_LONG);
                    addFailed.show();
                }
            }
        });
    }

}
