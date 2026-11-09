package com.avatar.tla;

import android.app.AlertDialog;
import android.app.NativeActivity;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class GameActivity extends NativeActivity {
    private WindowManager windowManager;
    private View gearButton;
    private View fpsOverlay;
    private TextView fpsText;
    private Handler handler;
    private Runnable fpsRunnable;
    private long lastFrameTime = 0;
    private int frameCount = 0;
    private boolean overlayAdded = false;

    private SharedPreferences sp;

    static {
        try {
            System.loadLibrary("ps2EntryRunner");
        } catch (Exception ignored) {}
    }

    public native void setCheatState(String cheatName, boolean isEnabled);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("ps2cfg", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(this::tryAddOverlay, 500);
    }

    private void tryAddOverlay() {
        if (overlayAdded || isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
        try {
            windowManager = getWindowManager();
            View decor = getWindow().getDecorView();
            if (decor == null || decor.getWindowToken() == null) {
                handler.postDelayed(this::tryAddOverlay, 300);
                return;
            }

            // Gear button (in-app overlay, no SYSTEM_ALERT_WINDOW)
            ImageButton gear = new ImageButton(this);
            gear.setImageResource(R.drawable.ic_gear_button);
            gear.setBackgroundColor(0x66000000);
            gear.setPadding(8, 8, 8, 8);
            gear.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            gear.setOnClickListener(v -> showSettingsDialog());

            WindowManager.LayoutParams gearParams = new WindowManager.LayoutParams(
                    dp(48), dp(48),
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            gearParams.gravity = Gravity.TOP | Gravity.END;
            gearParams.x = dp(8);
            gearParams.y = dp(8);
            gearParams.token = decor.getWindowToken();
            windowManager.addView(gear, gearParams);
            gearButton = gear;

            // FPS overlay
            fpsText = new TextView(this);
            fpsText.setText("FPS: --");
            fpsText.setTextColor(0xFF00FF00);
            fpsText.setTextSize(14);
            fpsText.setBackgroundColor(0x80000000);
            fpsText.setPadding(dp(6), dp(4), dp(6), dp(4));

            WindowManager.LayoutParams fpsParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            fpsParams.gravity = Gravity.TOP | Gravity.START;
            fpsParams.x = dp(8);
            fpsParams.y = dp(8);
            fpsParams.token = decor.getWindowToken();
            windowManager.addView(fpsText, fpsParams);
            fpsOverlay = fpsText;

            overlayAdded = true;
            startFpsCounter();
        } catch (Exception e) {
            // Retry once later; UI overlay is non-critical
            handler.postDelayed(this::tryAddOverlay, 500);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showSettingsDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(0xFF1A1A2E);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("In-Game Menu");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        // Tab 1: FPS & Device Info
        addSection(root, "--- FPS & Device Info ---");
        TextView fpsInfo = new TextView(this);
        fpsInfo.setText("FPS: " + (fpsText != null ? fpsText.getText() : "--"));
        fpsInfo.setTextColor(0xFFFFFFFF);
        root.addView(fpsInfo);
        TextView deviceInfo = new TextView(this);
        deviceInfo.setText("Device: " + Build.MODEL + " (" + Build.DEVICE + ")");
        deviceInfo.setTextColor(0xFFFFFFFF);
        deviceInfo.setPadding(0, 0, 0, dp(12));
        root.addView(deviceInfo);

        // Tab 2: Cheat
        addSection(root, "--- Cheat ---");
        String[] cheats = {"Infinite Health", "Infinite Chi", "Infinite Copper",
                "Infinite Stealth", "Double Damage", "One Hit Dishonor"};
        for (String cheat : cheats) {
            CheckBox cb = new CheckBox(this);
            cb.setText(cheat);
            cb.setTextColor(0xFFFFFFFF);
            cb.setChecked(sp.getBoolean(cheat, false));
            cb.setOnCheckedChangeListener((bv, checked) -> {
                sp.edit().putBoolean(cheat, checked).apply();
                try { setCheatState(cheat, checked); } catch (Throwable ignored) {}
            });
            root.addView(cb);
        }

        // Tab 3: Controller Layout
        addSection(root, "--- Atur Tata Letak Kontroler ---");
        TextView ctrlInfo = new TextView(this);
        ctrlInfo.setText("Drag tombol virtual untuk mengatur posisi (coming soon)");
        ctrlInfo.setTextColor(0xAAAAAAAA);
        ctrlInfo.setPadding(0, 0, 0, dp(8));
        root.addView(ctrlInfo);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] vButtons = {"A","B","X","Y","L1","R1","L2","R2","Start","Select"};
        for (String b : vButtons) {
            TextView tv = new TextView(this);
            tv.setText(b);
            tv.setTextColor(0xFFFFFFFF);
            tv.setBackgroundColor(0x44FFFFFF);
            tv.setPadding(dp(10), dp(6), dp(10), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            tv.setLayoutParams(lp);
            row.addView(tv);
        }
        root.addView(row);

        new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("Tutup", (d, w) -> d.dismiss())
                .show();
    }

    private void addSection(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFF00E5FF);
        t.setPadding(0, dp(8), 0, dp(4));
        root.addView(t);
    }

    private void startFpsCounter() {
        lastFrameTime = System.nanoTime();
        frameCount = 0;
        fpsRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                frameCount++;
                long now = System.nanoTime();
                long elapsed = now - lastFrameTime;
                if (elapsed >= 1_000_000_000L) {
                    float fps = (frameCount * 1_000_000_000f) / elapsed;
                    if (fpsText != null) fpsText.setText(String.format("FPS: %.1f", fps));
                    frameCount = 0;
                    lastFrameTime = now;
                }
                handler.postDelayed(this, 250);
            }
        };
        handler.postDelayed(fpsRunnable, 250);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && fpsRunnable != null) handler.removeCallbacks(fpsRunnable);
        if (windowManager != null) {
            try { if (gearButton != null) windowManager.removeViewImmediate(gearButton); } catch (Exception ignored) {}
            try { if (fpsOverlay != null) windowManager.removeViewImmediate(fpsOverlay); } catch (Exception ignored) {}
        }
    }
}
