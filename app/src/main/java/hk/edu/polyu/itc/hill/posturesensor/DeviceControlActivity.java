/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hk.edu.polyu.itc.hill.posturesensor;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Date;
import java.text.SimpleDateFormat;
import hk.edu.polyu.itc.hill.posturesensor.libmath.Quaternion;
import hk.edu.polyu.itc.hill.posturesensor.libmath.MathUtils;
import hk.edu.polyu.itc.hill.posturesensor.libmath.Vec3D;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static final int TIMER_WRITEDATA = 1;
    private static final int TIMER_INIT_ADJUST =5;
    private static final int MPU_TIMEOUT =10;
    private static final int BMP_TIMEOUT =100;
    private static final int BATTERY_TIMEOUT =1000;

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
//    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
   // private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

   //event stamp STAND=0 SIT=1 WALK=2
    private final int STAND = 1;
    private final int SIT = 2;
    private final int WALK = 3;


    private BluetoothGattCharacteristic mSensorDataCharacterstic = null;
    private BluetoothGattCharacteristic mBatterInfoCharacteristic = null;
    private BluetoothGattCharacteristic mBMPDataCharacteristic = null;

    TextView myLabel, banner, batteryLevel,myAngleText,myActivityText;
    ImageView aniView;


// Old stuff should be deleted.
// EditText myTextbox;
//    BluetoothSocket mmSocket;
//    BluetoothDevice mmDevice;
//    OutputStream mmOutputStream;
//    InputStream mmInputStream;
//    Thread workerThread;
//    byte[] readBuffer;
//    int readBufferPosition;
//    int counter;

    short x, y, z;
    short gx, gy, gz;//gyroscope
    short qw, qx, qy, qz;//quaternion

    Quaternion measurementQuat, offsetQuatReg;
    Quaternion offsetQuat = new Quaternion(1,0,0,0);
    double angle, yaw, pitch, roll;
    int temperature, pressure;//temperature sensor
    int timer_MPU=0, timer_BMP=0, timer_BATTERY=0, timer_InitAdjust= TIMER_INIT_ADJUST, timer_writeData=TIMER_WRITEDATA; //hack the  issue with assycronous read.
    double[] rv = {0, 0, 1};// initial ideal pos refvec
    double[] rvreg = {0, 0, 1};//register for temparary ref vec
    private byte[] bmpraw = new byte[8];
    private byte[] mpuraw = new byte [20];
    private byte[] offsetraw =new byte [8];
    volatile boolean stopWorker;
    boolean initFinish = false;
//    double sensitivity = 0; //sensitivity value for bad posture alert
    //private static final int REQUEST_ENABLE_BT = 0;
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final boolean D = true;
    static final float ALPHA = 0.15f; //this is used as parameter for low-pass filter
    float sensitivity = 0.15f; //0-1; 1 equals to no filter.
    double[] cvOld = null;

    enum posState {GOOD, BAD, DANGER, ERROR, INIT};

    short event= 0;
    String[] activities = {"Unknown", "Stand","Sit","Walk"};

    posState ps, psOld;
    long[] vbPattern1 = {0, 200, 500};
    long[] vbPattern2 = {0, 400, 100, 200};
    //New BLE code
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;




    private static final int REQUEST_ENABLE_BT = 0;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    //transmission request runnable, for schedual purpose:
    private Runnable requestMPU6050 = new Runnable()
    {
        @Override
        public void run()
        {
            if(mConnected )
            mBluetoothLeService.readCharacteristic(mSensorDataCharacterstic); // reading mpu6050.
        }
    };
    private Runnable requestBattery = new Runnable()
    {
        @Override
        public void run()
        {
            if(mConnected)
            mBluetoothLeService.readCharacteristic(mBatterInfoCharacteristic);//read battery level.
        }
    };

    private Runnable requestBMP = new Runnable()
    {
        @Override
        public void run()
        {
            if(mConnected)
            mBluetoothLeService.readCharacteristic(mBMPDataCharacteristic);//read BMP
        }
    };
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                //displayGattServices(mBluetoothLeService.getSupportedGattServices());
                selectGattCharacteristc(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {





                //select different type of data. all of them are encapsulate in ACTION_DATA_AVAILABLE intent. EXTRA_DATA: MPU6050  BMP_DATA: BMP sensor(temp+pressure). BATTERY_DATA: battery.
                // this hardware may have a bug preventing asyncronous read action. whcih means one has to wait till last readrequest fullfilled, meaning got a callback function already. hence
                // we do a little shuffle here to guarentee the sensor and other data are read in a cirular way. mpu6050->bmp->battery->(mpu6050again)

                if(intent.hasExtra(BluetoothLeService.EXTRA_DATA)) {
                    mpuraw = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                    displayDataToUI(mpuraw);


                    if (!initFinish) {

                        timer_InitAdjust--;
                        banner.setText(R.string.initial);
                    }


                    //Handler myHandler = new Handler();
                    //myHandler.postDelayed(requestBMP, 10);
                    if (timer_InitAdjust == 0) {

                        initFinish = true;
                        adjust();
                        timer_InitAdjust = 100; //reset the timer so it will never be triggered again after initfinish.
                    }
                    if (timer_BMP == 0) {

                        mBluetoothLeService.readCharacteristic(mBMPDataCharacteristic);
                        //reset a timer to limit the BMP_DATA refresh;
                        timer_BMP = BMP_TIMEOUT;
                    } else if (timer_BATTERY == 0) {

                        mBluetoothLeService.readCharacteristic(mBatterInfoCharacteristic);
                        //Log.e(TAG, "Battery Requested.");
                        timer_BATTERY = BATTERY_TIMEOUT;
                    } else {

                        timer_BMP --;
                        timer_BATTERY --;
                        //mBluetoothLeService.readCharacteristic(mSensorDataCharacterstic);
//                         mHandler = new Handler();
                        mHandler.postDelayed(requestMPU6050, 250);

                    }

                    //always write rawdata (recored every read action,
                    // Noted that at the very beginning bmp data is read first: see selectGatCharacteristic() method,
                    // this guarantees following line will always
                    // have valid current bmp and mpu data)
//                    Handler myHandler2 =new Handler();
                    mHandler.post(writeRawDataToStorage);


                }
                if (intent.hasExtra(BluetoothLeService.BMP_DATA)) {
                    //display mbp

                    bmpraw = intent.getByteArrayExtra(BluetoothLeService.BMP_DATA);
                    final ByteBuffer bb = ByteBuffer.allocate(8);
                    bb.order(ByteOrder.LITTLE_ENDIAN);
                    bb.put(bmpraw);
                    bb.rewind();
                    temperature=bb.getInt();
                    pressure=bb.getInt();
                    myLabel.setText("Temperature: " + String.format("%.2f", (double) temperature  / 10) + " °C " + "Pressure: " + String.format("%d", pressure)  + "Pa");
                    bb.clear();
                    //Handler myHandler = new Handler();
                    //myHandler.postDelayed(requestBattery, 10); //schedual another read after 1s.
                    mBluetoothLeService.readCharacteristic(mSensorDataCharacterstic);
                }
                if (intent.hasExtra(BluetoothLeService.BATTERY_DATA)) {
                    //reset a timer to limit the BMP_DATA refresh;

                    batteryLevel.setText("Batter Level: " + intent.getIntExtra(BluetoothLeService.BATTERY_DATA,0)+"%");
                    //Handler myHandler = new Handler();
                    //myHandler.postDelayed(requestMPU6050, 10);
                    mBluetoothLeService.readCharacteristic(mSensorDataCharacterstic);
                }
                //set a time counting the sensor data reading operation. This time is used to adjust how often we write the data storage.
                if (timer_writeData == 0){
                    timer_writeData = TIMER_WRITEDATA;// timer reset
//                    Handler myHandler = new Handler();
                    mHandler.postDelayed(writeDataToStorage, 0);




                }
                timer_writeData --;


            }

        }
    };




    private void clearUI() {
//        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
//        mDataField.setText(R.string.no_data);
    }

    void adjust() {
        rv = rvreg;
        offsetQuat = offsetQuatReg;

        mHandler.post(writeBeacon);
       ps = posState.GOOD;//kick it into good state after adjust. w
    }



    private Runnable writeDataToStorage = new Runnable()
    {
        @Override
        public void run()
        {
           if (isExternalStorageWritable()){
               SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd");
               Date now = new Date();
               String filename = formatter.format(now) + ".txt";

               File file = new File(getStorageDir("postureData"), filename); //initialize file with storageDir using the filename defined in filename variable

               // get current timestamp
               Long tsLong = System.currentTimeMillis()/100; //unit: per 100ms(0.1s)
               String ts = tsLong.toString();

               String string = ts +", "+event+", "+ x + ", " +y +", " + z + ", " + gx + ", " +gy + ", " + gz + ", " +String.format("%.2f", angle) + ", " + pressure + ", " +temperature + "\n";
               try {
                   FileOutputStream outputStream = new FileOutputStream(file, true);

                   outputStream.write(string.getBytes());
                   outputStream.close();
//                   Log.e(TAG, "file write success!");
               } catch (Exception e) {
                   e.printStackTrace();
               }

           }

        }
    };
    private Runnable writeRawDataToStorage = new Runnable()
    {
        @Override
        public void run()
        {
            if (isExternalStorageWritable()){
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd");
                Date now = new Date();
                String filename = formatter.format(now) + ".raw";

                File file = new File(getStorageDir("postureData"), filename); //initialize file with storageDir using the filename defined in filename variable

                // get current timestamp
                Long tsLong = System.currentTimeMillis();
//                String ts = tsLong.toString();
//
//                String string = ts +", "+event+", "+ x + ", " +y +", " + z + ", " + gx + ", " +gy + ", " + gz + ", " +String.format("%.2f", angle) + ", " + pressure + ", " +temperature + "\n";
                try {
                    FileOutputStream fos = new FileOutputStream(file, true);
 /*
       * To create DataOutputStream object from FileOutputStream use,
       * DataOutputStream(OutputStream os) constructor.
       *
       */

                    DataOutputStream dos = new DataOutputStream(fos);
                    /*
        * To write an int value to a file, use
        * void writeInt(int i) method of Java DataOutputStream class.
        *
        * This method writes specified int to output stream as 4 bytes value.
        */

                    dos.write(0xAA);//header
                    dos.writeLong(tsLong);
                    dos.write(mpuraw);
                    dos.write(bmpraw);
                    dos.writeShort(event);
                    dos.write(0x0a);//tail total 32


        /*
         * To close DataOutputStream use,
         * void close() method.
         *
         */

                    dos.close();

                fos.close();
//                   Log.e(TAG, "file write success!");
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

        }
    };
    private Runnable writeBeacon = new Runnable()
    {
        @Override
        public void run()
        {
            if (isExternalStorageWritable()){
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd");
                Date now = new Date();
                String filename = formatter.format(now) + "_Beacon.raw";

                File file = new File(getStorageDir("postureData"), filename); //initialize file with storageDir using the filename defined in filename variable

                // get current timestamp
                Long tsLong = System.currentTimeMillis();
//                String ts = tsLong.toString();
//
//                String string = ts +", "+event+", "+ x + ", " +y +", " + z + ", " + gx + ", " +gy + ", " + gz + ", " +String.format("%.2f", angle) + ", " + pressure + ", " +temperature + "\n";
                try {
                    FileOutputStream fos = new FileOutputStream(file, true);
 /*
       * To create DataOutputStream object from FileOutputStream use,
       * DataOutputStream(OutputStream os) constructor.
       *
       */

                    DataOutputStream dos = new DataOutputStream(fos);
                    /*
        * To write an int value to a file, use
        * void writeInt(int i) method of Java DataOutputStream class.
        *
        * This method writes specified int to output stream as 4 bytes value.
        */

                    dos.write(0xAA);//header
                    dos.writeLong(tsLong);
                    dos.writeShort(qw);
                    dos.writeShort(qx);
                    dos.writeShort(qy);
                    dos.writeShort(qz);
                    dos.write(0x0a);//tail total 32


        /*
         * To close DataOutputStream use,
         * void close() method.
         *
         */

                    dos.close();

                    fos.close();
//                   Log.e(TAG, "file write success!");
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

        }
    };
//
    /* Checks if external storage is available for read and write */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }
    // get an public directory for persistent data storage.
    public File getStorageDir(String dirName) {
        // Get the directory for the user's public pictures directory.
//        File file = new File(Environment.getExternalStoragePublicDirectory(
//                Environment.DIRECTORY_DOCUMENTS), dirName);
        File file = new File(Environment.getExternalStorageDirectory()+"/Documents/"+dirName);

        if (!file.mkdirs() && !file.isDirectory()) {
            Log.e(TAG, "Directory not created");
        }
        return file;
    }



    private void displayDataToUI(byte[] data) {
        final Vibrator vb = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        final NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        final Uri notiySound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        final Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (data != null && data.length > 0) {
//                final StringBuilder stringBuilder = new StringBuilder(data.length);
//                for (byte byteChar : data)
//                    stringBuilder.append(String.format("%02X ", byteChar));
//                mDataField.setText(new String(data) + "\n" + stringBuilder.toString());
                final ByteBuffer bb = ByteBuffer.allocate(20);
                bb.order(ByteOrder.LITTLE_ENDIAN);
                bb.put(data);
                bb.rewind();
                x = bb.getShort();
                y = bb.getShort();
                z = bb.getShort();
                rvreg = new double[]{x, y, z};//put temparary value to rvreg register.


                //keepon reading for temperature sensor MPU6055 only.
                //Short tmp = bb.getShort();
                gx = bb.getShort();
                gy = bb.getShort();
                gz = bb.getShort();

                //reading for quaternion
                qw = bb.getShort();
                qx = bb.getShort();
                qy = bb.getShort();
                qz = bb.getShort();

                Quaternion rawQuat = new Quaternion(qw,qx,qy,qz);
                rawQuat.normalize();
                offsetQuatReg = rawQuat;
                double[] cv = {x, y, z};
                //do the low-pass filtering on cv;
                cv = lowPass(cv, cvOld);
                //update the history of cv for next iteration.
                cvOld = cv;
                //get the angle between rv  and cv;
                angle = getAngle(rv, cv);

//                measurementQuat = offsetQuat.getConjugate().multiply(rawQuat).normalize(); //the cdisabled for now.





                //angle = MathUtils.degrees(measurementQuat.toAxisAngle()[0]); //this is true angle we want to remove rotation along z axis
                // yaw: (about Z axis)
                //yaw = Math.atan2(2 * measurementQuat.x * measurementQuat.y - 2 * measurementQuat.w * measurementQuat.z, 2 * measurementQuat.w * measurementQuat.w + 2 * measurementQuat.x * measurementQuat.x - 1);
                //Vec3D v = new Vec3D(0,0,1);
                //Quaternion yawOffset = new Quaternion(-(float)yaw, v);
                //measurementQuat.z=0;

//                measurementQuat.normalize(); disable for now.

//                Log.e(TAG, "xyz is"+measurementQuat.toAxisAngle()[1]+" "+measurementQuat.toAxisAngle()[2]+" "+measurementQuat.toAxisAngle()[3]);
                // pitch: (nose up/down, about Y axis)
                //pitch = atan(x / sqrt(y*y + z*z));
                // roll: (tilt left/right, about X axis)
                //roll = atan(y / sqrt(x*x + gravityz*gravityz));

//                pitch = Math.asin (-2.0*(measurementQuat.x*measurementQuat.y-measurementQuat.w*measurementQuat.z));
//                roll = Math.atan2(2*(measurementQuat.w*measurementQuat.x+measurementQuat.y*measurementQuat.z),1-2*(measurementQuat.x*measurementQuat.x+measurementQuat.z*measurementQuat.z));
//                angle = MathUtils.degrees((float)roll);
//                angle = MathUtils.degrees(measurementQuat.toAxisAngle()[0]);
                // start animation to show real-time tilting.
//                                            if(D) Log.d(TAG, "calcAngle " + angle);
//                                            ImageView aniView = (ImageView) findViewById(R.id.imageView1);
//                ObjectAnimator animation1 = ObjectAnimator.ofFloat(aniView, "rotation", (x > 0 ? (float) angle : -(float) angle));
                if (initFinish) {


                ObjectAnimator animation1 = ObjectAnimator.ofFloat(aniView, "rotation", -(float)angle);
//                                            animation1.setDuration(180);
                animation1.start();
                myAngleText.setText("Angle: "+ String.format("%.0f",angle) + "°");
                    //TODO: disabled for demo, it is suposed to show the current activity.
//                myActivityText.setText(activities[event]);

                }
                //echo the debugging stuff such as angle at the bottom textview object.
//                                            myLabel.setText("x="+x+"y="+y+"z="+z+"angle:"+angle+"x="+rv[0]+"y="+rv[1]+"z="+rv[2]);

                //doing notification of current posture.
                if (ps != posState.INIT) {//init loop until it was kicked into good state.
//                    if (angle <= 15*(1-sensitivity)) {
                    if (angle <= 15) {
                        ps = posState.GOOD;


//                                            myLabel.setText("x="+x+"y="+y+"z="+z+"angle:"+angle+"x="+rv[0]+"y="+rv[1]+"z="+rv[2]);
//                    } else if (angle <= 60) {
//                        ps = posState.BAD;
//
//
//                    } else if (angle <= 90) {
//                        ps = posState.DANGER;

                    } else {
                        ps = posState.BAD;


                    }
                }
                //show Temperature:
                //myLabel.setText("Temperature: " + String.format("%.2f", (temperature + 12412.0) / 340) + " degrees Celsius");
                //check if state changes, if yes start corresponding event.


                if (ps != psOld) {
//                                                myLabel.setText("x="+x+"y="+y+"z="+z+"angle:"+angle+"x="+rv[0]+"y="+rv[1]+"z="+rv[2]);

                    switch (ps) {
                        case INIT:
                            banner.setText(R.string.initial);//this case should never happen.
                            vb.cancel();
                            break;
                        case GOOD:
                            banner.setText(R.string.goodPos);
                            vb.cancel();
                            aniView.setImageDrawable(getResources().getDrawable(R.drawable.upper));
                            break;
                        case BAD:
                            banner.setText(R.string.badPos);
                            vb.cancel();
                            vb.vibrate(vbPattern1, 0);
                            aniView.setImageDrawable(getResources().getDrawable(R.drawable.upper));
                            Notification.Builder mBuilder = new Notification.Builder(getApplicationContext())
                                    .setSmallIcon(R.drawable.ic_launcher)
                                    .setContentTitle("pMon")
                                    .setContentText("Keep Straight")
                                    .setSound(notiySound); //This sets the sound to play
                            notificationManager.notify(0, mBuilder.build());
                            break;
                        case DANGER:
                            banner.setText(R.string.dangerPos);
                            vb.cancel();
                            vb.vibrate(vbPattern2, 0);
                            aniView.setImageDrawable(getResources().getDrawable(R.drawable.line_red));
                            mBuilder = new Notification.Builder(getApplicationContext())
                                    .setSmallIcon(R.drawable.ic_launcher)
                                    .setContentTitle("pMon")
                                    .setContentText("Danger")
                                    .setSound(alarmSound); //This sets the sound to play
                            notificationManager.notify(0, mBuilder.build());
                            break;
                        case ERROR:
                            banner.setText(R.string.systemError);
                            vb.cancel();
                            break;

                    }
                }
                psOld = ps;
                //clear the bytebuffer for next read.



                bb.clear();

            }

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.demopos_main);

        //intialize handler for runables
        mHandler = new Handler();

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        psOld = posState.INIT;//should equal to ps;
        ps=posState.INIT;

        // Sets up UI references.
       // ((TextView) findViewById(R.id.device_address)).setText("Use this slider adjust sensitivity.");
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.label);
        batteryLevel = (TextView) findViewById(R.id.batterLevel);
        myAngleText = (TextView) findViewById(R.id.textAngle);
        //myActivityText = (TextView) findViewById(R.id.activity);
        float dest = 0;//used by animation

        aniView = (ImageView) findViewById(R.id.imageView1);
        //Button openButton = (Button) findViewById(R.id.open);
        Button adjustButton = (Button) findViewById(R.id.adjust);
        final TextView sensitivityLabel = (TextView) findViewById(R.id.sensView);
        final SeekBar sensitivityBar = (SeekBar) findViewById(R.id.seekBar);
        //Button closeButton = (Button) findViewById(R.id.close);
        myLabel = (TextView) findViewById(R.id.label);
        banner = (TextView) findViewById(R.id.banner);
        // Initialize the textview with '0'.
        //Set initial posision as 0.15.
//        sensitivityBar.setProgress((int) (sensitivity * (float)sensitivityBar.getMax()));
        sensitivityBar.setProgress(50);
        sensitivityLabel.setText("Sensitivity: " + sensitivityBar.getProgress() + "/" + sensitivityBar.getMax());


        sensitivityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progress = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean b) {
                progress = progressValue;

                sensitivityLabel.setText("Sensitivity: " + progress + "/" + seekBar.getMax());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Toast.makeText(getApplicationContext(), "Adjusting Sensitivity", Toast.LENGTH_SHORT).show();

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                sensitivity = (float) ALPHA * progress/seekBar.getMax();
                Log.d(TAG, "Sensitivity=" + sensitivity);
                Toast.makeText(getApplicationContext(), "New Sensitivity Setting Applied Successfully!", Toast.LENGTH_SHORT).show();

            }
        });

//        myTextbox = (EditText)findViewById(R.id.entry);
        //Open Button
//        openButton.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                findBT();
//                if (btFound) {
//                    myLabel.setText("Bluetooth Device Found");
//                }
//            }
//        });
        //adjust Button
        adjustButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                adjust();

            }
        });


//        Button stand = (Button) findViewById(R.id.stand);
//        stand.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//
//                event= STAND;
//            }
//        });
//        Button sit = (Button) findViewById(R.id.sit);
//        sit.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//
//                event= SIT;
//
//            }
//        });
//        Button walk = (Button) findViewById(R.id.walk);
//        walk.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//
//                event= WALK;
//
//            }
//        });
//
//        Button reset = (Button) findViewById(R.id.reset);
//        reset.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//
//                event= 0;
//
//            }
//        });


        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);


        //intializ notification stuff

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
//        if (mBluetoothLeService != null) {
//            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
//            Log.d(TAG, "Connect request result=" + result);
//        }
    }

//    @Override
//    protected void onPause() {
//        super.onPause();
//    //    unregisterReceiver(mGattUpdateReceiver);
//    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mGattUpdateReceiver);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        final Vibrator vb = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        vb.cancel();
        if (mHandler!=null)
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.gatt_services, menu);
//        if (mConnected) {
//            menu.findItem(R.id.menu_connect).setVisible(false);
//            menu.findItem(R.id.menu_disconnect).setVisible(true);
//        } else {
//            menu.findItem(R.id.menu_connect).setVisible(true);
//            menu.findItem(R.id.menu_disconnect).setVisible(false);
//        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                //requestTrans();
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                mSensorDataCharacterstic = null;
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }




    private void selectGattCharacteristc(List<BluetoothGattService> gattServices) {
        // when found the right characteristic, it supposed to start the inquriy: readcharacteristic imemdialty, but it seems one can only do one inquiry at a time.
        // so I figured a rotation inqury method which let them take turns. check broadcast receiver.
        if (gattServices == null ) return;
        for (BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                if (gattCharacteristic.getUuid().equals(UUID.fromString(SampleGattAttributes.UUID_POSTURE_SENSING_DATA_STREAM))) {
                    mSensorDataCharacterstic = gattCharacteristic;
//                    mBluetoothLeService.readCharacteristic(mSensorDataCharacterstic);

                    mHandler.postDelayed(requestBMP, 0); //schedual another read after 1s.
                }

                if ( gattCharacteristic.getUuid().equals(UUID.fromString(SampleGattAttributes.UUID_BATTERY_LEVEL))) {
                    mBatterInfoCharacteristic = gattCharacteristic;
//                    mBluetoothLeService.readCharacteristic(mBatterInfoCharacteristic);
//                    Handler myHandler = new Handler();
//                    myHandler.postDelayed(requestBattery, 1000); //schedual another read after 1s.
                }
                if (gattCharacteristic.getUuid().equals(UUID.fromString(SampleGattAttributes.UUID_BMP))) {
                    mBMPDataCharacteristic = gattCharacteristic;
//                    mBluetoothLeService.readCharacteristic(mBMPDataCharacteristic);
//                    Handler myHandler = new Handler();
//                    myHandler.postDelayed(requestBMP, 2000); //schedual another read after 1s.
                }

            }
        }
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
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


    ///////////////////////////////////////////////////



    /////////////////////////////////
    //math related
    //
    //
    /////////////////////////////////

    //use vector angel method. a.b=\a\*\b\*cos(angle(a,b)) to calculate angle.
    double getAngle(double[] initialVec, double[] currentVec) {
        double m1, m2, d;//2 mode for both vector. and dot product between two vec：d;
        m1 = m2 = d = 0;
        int i = 0;
        while (i < initialVec.length) {
            m1 += Math.pow(initialVec[i], 2);
            m2 += Math.pow(currentVec[i], 2);
            d += initialVec[i] * currentVec[i];
            i++;
        }
        return Math.toDegrees(Math.acos(d / (Math.sqrt(m1 * m2))));
    }

    //
    // low-pass filter for accelarometer.
    // @see http://en.wikipedia.org/wiki/Low-pass_filter#Algorithmic_implementation
    // @see http://developer.android.com/reference/android/hardware/SensorEvent.html#values
    //
    protected double[] lowPass(double[] input, double[] output) {
        if (output == null) return input;

        for (int i = 0; i < input.length; i++) {
//            output[i] = output[i] + ALPHA * (input[i] - output[i]);
            output[i] = output[i] + sensitivity * (input[i] - output[i]);
        }
        return output;
    }



}
