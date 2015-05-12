package hk.edu.polyu.itc.hill.posturesensor;

import android.bluetooth.BluetoothDevice;

public class BluetoothDeviceWrapper {
    BluetoothDevice mBtDevice = null;
    boolean selected = false;
    public BluetoothDeviceWrapper(final BluetoothDevice mBtDevice, boolean selected){
        super();
        this.mBtDevice = mBtDevice;
        this.selected = selected;

    }
    //BluetoothDevice methods:
    public String getName(){
        return mBtDevice.getName();
    }
    public String getAddress(){
          return mBtDevice.getAddress();
}
    public boolean isSelected(){
        return selected;

    }
    public void setSelected (boolean selected){
        this.selected = selected;
    }


    //overide
    @Override
    public boolean equals(Object object)
    {
        boolean sameSame = false;

        if (object != null && object instanceof BluetoothDeviceWrapper)
        {
            sameSame = this.mBtDevice.equals (((BluetoothDeviceWrapper) object).mBtDevice);
        }

        return sameSame;
    }

    @Override
    public int hashCode() {
        return mBtDevice.hashCode();
    }
}
