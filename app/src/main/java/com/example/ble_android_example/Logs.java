package com.example.ble_android_example;

import android.util.Log;

public class Logs {
    private static final String TAG="BTC Template";
    public static boolean mIsEnabled = true;

    public static void d(String msg) {
        if(mIsEnabled) {
            Log.d(TAG, msg);
        }
    }

    public static void d(String tag, String msg) {
        if(mIsEnabled) {
            Log.d(tag,msg);
        }
    }

    public static void i(String tag, String msg) {
        if(mIsEnabled) {
            Log.i(tag, msg);
        }
    }

    public static void e(String tag, String msg) {
        if(mIsEnabled){
            Log.e(tag, msg);
        }
    }

    public static void w(String tag, String msg) {
        if(mIsEnabled) {
            Log.w(tag, msg);
        }
    }
}
