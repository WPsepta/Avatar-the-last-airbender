package com.avatar.tla;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

public class CheatOverlayService extends Service {
    private WindowManager windowManager;
    private View floatingButton;
    private PopupWindow popup;
    private SharedPreferences sp;
    private WindowManager.LayoutParams params;

    static {
        System.loadLibrary("ps2EntryRunner");
    }

    public native void setCheatState(String cheatName, boolean isEnabled);

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences("ps2cfg", MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingButton = new View(this);
        floatingButton.setBackgroundResource(R.drawable.ic_cheat_button);

        params = new WindowManager.LayoutParams(
                140, 140,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 200;

        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                        }
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        windowManager.updateViewLayout(floatingButton, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            showPopup();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingButton, params);
    }

    private void showPopup() {
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setBackgroundColor(0xDD1A1A2E);

        String[] cheats = {"Infinite Health", "Infinite Chi", "Infinite Copper",
                "Infinite Stealth", "Double Damage", "One Hit Dishonor"};

        for (String cheat : cheats) {
            CheckBox cb = new CheckBox(this);
            cb.setText(cheat);
            cb.setTextColor(0xFFFFFFFF);
            cb.setChecked(sp.getBoolean(cheat, false));
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sp.edit().putBoolean(cheat, isChecked).apply();
                try {
                    setCheatState(cheat, isChecked);
                } catch (UnsatisfiedLinkError e) {
                    // JNI not available
                }
            });
            layout.addView(cb);
        }

        popup = new PopupWindow(layout, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.showAtLocation(floatingButton, Gravity.CENTER, 0, 0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingButton != null) {
            try {
                windowManager.removeView(floatingButton);
            } catch (Exception ignored) {}
        }
        if (popup != null && popup.isShowing()) popup.dismiss();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}