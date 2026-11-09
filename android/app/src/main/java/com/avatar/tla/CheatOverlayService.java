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
        floatingButton.setBackgroundResource(android.R.drawable.btn_star_big_on);
        floatingButton.setAlpha(0.8f);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                120, 120,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 200;

        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingButton, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        // If not moved much, treat as click
                        if (Math.abs(event.getRawX() - initialTouchX) < 10 &&
                            Math.abs(event.getRawY() - initialTouchY) < 10) {
                            showPopup(params);
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingButton, params);
    }

    private void showPopup(WindowManager.LayoutParams anchorParams) {
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(0xDD222222);

        String[] cheats = {"Infinite Health", "Infinite Chi", "Infinite Copper",
                "Infinite Stealth", "Double Damage", "One Hit Dishonor"};

        for (String cheat : cheats) {
            CheckBox cb = new CheckBox(this);
            cb.setText(cheat);
            cb.setTextColor(0xFFFFFFFF);
            cb.setChecked(sp.getBoolean(cheat, false));
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sp.edit().putBoolean(cheat, isChecked).apply();
                setCheatState(cheat, isChecked);
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
        if (floatingButton != null) windowManager.removeView(floatingButton);
        if (popup != null && popup.isShowing()) popup.dismiss();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}