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

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    //Edit by Hill
    public static String UUID_POSTURE_SENSING_DATA_STREAM = "0000ffb6-0000-1000-8000-00805f9b34fb";
    public static String UUID_POSTURE_SENSING_DATA_STREAM_NRF51 = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";
    public static String UUID_POSTURE_SENSING_CONTROL = "0000ffe9-0000-1000-8000-00805f9b34fb";
    public static String UUID_BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb";
    public static String UUID_BMP = "0000ffb7-0000-1000-8000-00805f9b34fb";
    //Edit by Hill End

    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");

        //Edit by Hill
        // add a new uuid
        attributes.put("0000ffe0-0000-1000-8000-00805f9b34fb", "Posture Sensing Service");
        attributes.put("0000ffe5-0000-1000-8000-00805f9b34fb", "Control Service");
        //Edit by Hill End
        // Sample Characteristics.
        //Edit by Hill
        // add a new Characteristic.
        attributes.put(UUID_POSTURE_SENSING_DATA_STREAM, "Posture Sensing Data Stream");
        attributes.put(UUID_POSTURE_SENSING_CONTROL, "Posture Sensing Control");
        //Edit by Hill End
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
