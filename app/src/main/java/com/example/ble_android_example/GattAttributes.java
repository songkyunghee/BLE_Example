package com.example.ble_android_example;

import java.util.HashMap;

public class GattAttributes {

    private static HashMap<String, String> attributes = new HashMap();

    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String HM_RX_TX = "0000ffe1-0000-1000-8000-00805f9b34fb";

    static {
        attributes.put(HM_RX_TX,"RX/TX data");
    }
}
