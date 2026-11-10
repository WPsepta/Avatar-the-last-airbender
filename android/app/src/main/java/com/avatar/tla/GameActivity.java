package com.avatar.tla;

import android.app.NativeActivity;
import android.os.Bundle;

public class GameActivity extends NativeActivity {
    static {
        try {
            System.loadLibrary("ps2EntryRunner");
        } catch (Exception ignored) {}
    }

    // Kept for JNI compatibility; overlay UI removed to avoid conflicts with native surface.
    public native void setCheatState(String cheatName, boolean isEnabled);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
