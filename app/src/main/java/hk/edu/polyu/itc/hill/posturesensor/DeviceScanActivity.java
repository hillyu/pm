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

import android.app.Activity;
import android.app.ListActivity;
import 	android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity {
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private ArrayList<String> mDeviceName = new ArrayList<String>();
    private ArrayList<String> mDeviceAddrs = new ArrayList<String> ();


    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_scan);
        getActionBar().setTitle(R.string.title_devices);
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

//        //set a static header toshow the instructions
//
//        ListView lv = getListView();
//        LayoutInflater inflater = getLayoutInflater();
//        View header = inflater.inflate(R.layout.header, lv, false);
//        lv.addHeaderView(header, null, false);

//
//        final String[] list = {"wrenches","hammers","drills","screwdrivers","saws","chisels","fasteners"};
//
//        // Initializing An ArrayAdapter Object for the ListActivity
//        final ArrayAdapter<String> adapter = new ArrayAdapter<String> (this, android.R.layout.simple_list_item_multiple_choice, list);



    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setVisible(true);
            menu.findItem(R.id.menu_confirm).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_confirm).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
            case R.id.menu_confirm:
                final Intent intent = new Intent(this, DeviceControlActivity.class);
                for (int j = mLeDeviceListAdapter.getCount()-1; j>=0; j--){
                    if (mLeDeviceListAdapter.getDevice(j).isSelected()){
                        mDeviceName.add(mLeDeviceListAdapter.getDevice(j).getName());
                        mDeviceAddrs.add(mLeDeviceListAdapter.getDevice(j).getAddress());

                    }
                }
                if(!mDeviceAddrs.isEmpty()){
                intent.putStringArrayListExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, mDeviceName);
                intent.putStringArrayListExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, mDeviceAddrs);
                startActivity(intent);}
                else{
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
                    builder1.setMessage(R.string.instruction);
                    builder1.setCancelable(true);
                    builder1.setPositiveButton("I understand",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });


                    AlertDialog alert11 = builder1.create();
                    alert11.show();
                }
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();

        setListAdapter(mLeDeviceListAdapter);

        //testing code:
//        final String[] list = {"wrenches","hammers","drills","screwdrivers","saws","chisels","fasteners"};

        // Initializing An ArrayAdapter Object for the ListActivity
//        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, list);
//        setListAdapter(adapter);
        //testing codeend.

        scanLeDevice(true);

        //instruction using alert:
        AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
        builder1.setMessage(R.string.instruction);
        builder1.setCancelable(true);
        builder1.setPositiveButton("I understand",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });


        AlertDialog alert11 = builder1.create();
        alert11.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {// TODO: Not working after adding checkbox.
//        final BluetoothDeviceWrapper device = mLeDeviceListAdapter.getDevice(position);
//        if (device == null) return;


//        final Intent intent = new Intent(this, DeviceControlActivity.class);
//        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
//        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());




//    mDeviceName.add(device.getName());
//    mDeviceAddrs.add(device.getAddress());
    if (mScanning) {
        mBluetoothAdapter.stopLeScan(mLeScanCallback);
        mScanning = false;
    }
}

        //startActivity(intent);


    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDeviceWrapper> mLeDevices;
//        private ArrayList<BluetoothDeviceWrapper> mWrappers;
        private LayoutInflater mInflator;
//        private ArrayList<Boolean> isChecked;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDeviceWrapper>();
//            mWrappers = new ArrayList<BluetoothDeviceWrapper>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
//            isChecked =new ArrayList<Boolean>();
        }

        public void addDevice(BluetoothDeviceWrapper device) {

            if(!mLeDevices.contains(device)) {
//                BluetoothDeviceWrapper wrapperDevice = new BluetoothDeviceWrapper(device,false);
                mLeDevices.add(device);
//                mWrappers.add(wrapperDevice);
            }
        }

//        public BluetoothDeviceWrapper getWrapperDevice(int position) {
//            return mWrappers.get(position);
//        }

        public BluetoothDeviceWrapper getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView( final int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device,null );
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                viewHolder.deviceSelectionStatus = (CheckBox) view.findViewById(R.id.checkBox);
                view.setTag(viewHolder);


                viewHolder.deviceSelectionStatus.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean state) {
                        CompoundButton cb = buttonView;
                        BluetoothDeviceWrapper btDevice = (BluetoothDeviceWrapper) cb.getTag();
                        btDevice.setSelected(cb.isChecked());

                    }
                });


            } else {
                viewHolder = (ViewHolder) view.getTag();
            }


            BluetoothDeviceWrapper device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

//            viewHolder.deviceSelectionStatus.setChecked(device.isSelected());
            viewHolder.deviceSelectionStatus.setTag(device);

            return view;
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            final BluetoothDeviceWrapper mWrapperDevice = new BluetoothDeviceWrapper(device, false);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.addDevice(mWrapperDevice);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        CheckBox deviceSelectionStatus;
    }
}
