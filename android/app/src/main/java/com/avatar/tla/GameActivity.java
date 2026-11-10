package com.avatar.tla;

import android.app.NativeActivity;
import android.os.Bundle;

public class GameActivity extends NativeActivity {
    static {
        try {
            System.loadLibrary("ps2EntryRunner");
        } catch (Throwable ignored) {}
    }

    // Stub for JNI compatibility if needed by native side
    public native void setCheatState(String cheatName, boolean isEnabled);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
