package com.avatar.tla;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

public class GameOverlayService extends Service {
    private WindowManager windowManager;
    private View floatingButton;
    private PopupWindow popup;
    private SharedPreferences sp;
    private WindowManager.LayoutParams params;

    private Handler fpsHandler;
    private Runnable fpsRunnable;
    private long lastFrameTime = 0;
    private int frameCount = 0;
    private float currentFps = 0;
    private TextView fpsText;

    static {
        try {
            System.loadLibrary("ps2EntryRunner");
        } catch (Exception ignored) {}
    }

    public native void setCheatState(String cheatName, boolean isEnabled);

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences("ps2cfg", MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingButton = new View(this);
        floatingButton.setBackgroundResource(R.drawable.ic_gear_button);

        params = new WindowManager.LayoutParams(
                120, 120,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 100;

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
                            showPanel();
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingButton, params);
    }

    private void showPanel() {
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
            stopFpsCounter();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(0xDD1A1A2E);

        // Title
        TextView title = new TextView(this);
        title.setText("In-Game Menu");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        // Section 1: FPS & Device Info
        TextView section1 = new TextView(this);
        section1.setText("--- FPS & Device Info ---");
        section1.setTextColor(0xFF00E5FF);
        section1.setPadding(0, 8, 0, 8);
        root.addView(section1);

        fpsText = new TextView(this);
        fpsText.setText("FPS: --");
        fpsText.setTextColor(0xFFFFFFFF);
        fpsText.setPadding(0, 0, 0, 4);
        root.addView(fpsText);

        TextView deviceText = new TextView(this);
        deviceText.setText("Device: " + android.os.Build.MODEL + " (" + android.os.Build.DEVICE + ")");
        deviceText.setTextColor(0xFFFFFFFF);
        deviceText.setPadding(0, 0, 0, 16);
        root.addView(deviceText);

        // Section 2: Cheat
        TextView section2 = new TextView(this);
        section2.setText("--- Cheat ---");
        section2.setTextColor(0xFF00E5FF);
        section2.setPadding(0, 8, 0, 8);
        root.addView(section2);

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
                } catch (Exception ignored) {}
            });
            root.addView(cb);
        }

        // Section 3: Controller Layout
        TextView section3 = new TextView(this);
        section3.setText("--- Atur Tata Letak Kontroler ---");
        section3.setTextColor(0xFF00E5FF);
        section3.setPadding(0, 16, 0, 8);
        root.addView(section3);

        TextView ctrlInfo = new TextView(this);
        ctrlInfo.setText("Drag tombol virtual untuk mengatur posisi (coming soon)");
        ctrlInfo.setTextColor(0xAAAAAAAA);
        ctrlInfo.setPadding(0, 0, 0, 8);
        root.addView(ctrlInfo);

        // Virtual button placeholders
        String[] vButtons = {"A", "B", "X", "Y", "L1", "R1", "L2", "R2", "Start", "Select"};
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String btn : vButtons) {
            TextView btnView = new TextView(this);
            btnView.setText(btn);
            btnView.setTextColor(0xFFFFFFFF);
            btnView.setBackgroundColor(0x44FFFFFF);
            btnView.setPadding(16, 8, 16, 8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(4, 4, 4, 4);
            btnView.setLayoutParams(lp);
            btnRow.addView(btnView);
        }
        root.addView(btnRow);

        popup = new PopupWindow(root, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.showAtLocation(floatingButton, Gravity.CENTER, 0, 0);

        startFpsCounter();
    }

    private void startFpsCounter() {
        fpsHandler = new Handler(Looper.getMainLooper());
        lastFrameTime = System.nanoTime();
        frameCount = 0;
        fpsRunnable = new Runnable() {
            @Override
            public void run() {
                frameCount++;
                long now = System.nanoTime();
                long elapsed = now - lastFrameTime;
                if (elapsed >= 1_000_000_000L) {
                    currentFps = (frameCount * 1_000_000_000f) / elapsed;
                    if (fpsText != null) {
                        fpsText.setText(String.format("FPS: %.1f", currentFps));
                    }
                    frameCount = 0;
                    lastFrameTime = now;
                }
                if (popup != null && popup.isShowing()) {
                    fpsHandler.postDelayed(this, 16);
                }
            }
        };
        fpsHandler.postDelayed(fpsRunnable, 16);
    }

    private void stopFpsCounter() {
        if (fpsHandler != null && fpsRunnable != null) {
            fpsHandler.removeCallbacks(fpsRunnable);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopFpsCounter();
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