package com.avatar.tla;

import android.app.NativeActivity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;

public class GameActivity extends NativeActivity {
    private ImageButton gearButton;
    private TextView fpsText;
    private Handler fpsHandler;
    private Runnable fpsRunnable;
    private long lastFrameTime = 0;
    private int frameCount = 0;
    private float currentFps = 0;
    private boolean isRunning = true;

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
        setContentView(R.layout.game_activity);

        sp = getSharedPreferences("ps2cfg", MODE_PRIVATE);

        gearButton = findViewById(R.id.btnGear);
        fpsText = findViewById(R.id.tvFps);

        gearButton.setOnClickListener(v -> showSettingsDialog());

        startFpsCounter();
    }

    private void bacaLog() {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("logcat -d -s PS2RecompJNI:* AndroidRuntime:E *:F");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\\n");
                }
                reader.close();
                process.waitFor();
                final String logOutput = sb.toString();
                runOnUiThread(() -> {
                    if (fpsText != null) {
                        fpsText.setText(logOutput.isEmpty() ? "Tidak ada log" : logOutput.substring(0, Math.min(logOutput.length(), 200)));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (fpsText != null) {
                        fpsText.setText("Gagal baca log: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    private void showSettingsDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(0xDD1A1A2E);

        TextView title = new TextView(this);
        title.setText("In-Game Menu");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        // Tab 1: FPS & Device Info
        TextView tab1 = new TextView(this);
        tab1.setText("--- FPS & Device Info ---");
        tab1.setTextColor(0xFF00E5FF);
        tab1.setPadding(0, 8, 0, 8);
        root.addView(tab1);

        TextView fpsInfo = new TextView(this);
        fpsInfo.setText("FPS: " + String.format("%.1f", currentFps));
        fpsInfo.setTextColor(0xFFFFFFFF);
        fpsInfo.setPadding(0, 0, 0, 4);
        root.addView(fpsInfo);

        TextView deviceInfo = new TextView(this);
        deviceInfo.setText("Device: " + android.os.Build.MODEL + " (" + android.os.Build.DEVICE + ")");
        deviceInfo.setTextColor(0xFFFFFFFF);
        deviceInfo.setPadding(0, 0, 0, 16);
        root.addView(deviceInfo);

        // Tab 2: Cheat
        TextView tab2 = new TextView(this);
        tab2.setText("--- Cheat ---");
        tab2.setTextColor(0xFF00E5FF);
        tab2.setPadding(0, 8, 0, 8);
        root.addView(tab2);

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

        // Tab 3: Controller Layout
        TextView tab3 = new TextView(this);
        tab3.setText("--- Atur Tata Letak Kontroler ---");
        tab3.setTextColor(0xFF00E5FF);
        tab3.setPadding(0, 16, 0, 8);
        root.addView(tab3);

        TextView ctrlInfo = new TextView(this);
        ctrlInfo.setText("Drag tombol virtual untuk mengatur posisi (coming soon)");
        ctrlInfo.setTextColor(0xAAAAAAAA);
        ctrlInfo.setPadding(0, 0, 0, 8);
        root.addView(ctrlInfo);

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

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setView(root);
        builder.setPositiveButton("Tutup", (d, w) -> d.dismiss());
        builder.show();
    }

    private void startFpsCounter() {
        fpsHandler = new Handler(Looper.getMainLooper());
        lastFrameTime = System.nanoTime();
        frameCount = 0;
        fpsRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
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
                fpsHandler.postDelayed(this, 16);
            }
        };
        fpsHandler.postDelayed(fpsRunnable, 16);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (fpsHandler != null && fpsRunnable != null) {
            fpsHandler.removeCallbacks(fpsRunnable);
        }
    }
}