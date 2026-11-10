package com.avatar.tla;

import android.app.Dialog;
import android.app.NativeActivity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class GameActivity extends NativeActivity {
    private Handler handler;
    private Dialog logDialog;
    private TextView logTextView;
    private ScrollView logScrollView;
    private Runnable logUpdater;
    private volatile boolean dialogShowing = false;

    static {
        try {
            System.loadLibrary("ps2EntryRunner");
        } catch (Throwable ignored) {}
    }

    public native void setCheatState(String cheatName, boolean isEnabled);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        // Show floating log dialog shortly after start (on UI thread)
        handler.postDelayed(this::showFloatingLog, 1500);
    }

    private void showFloatingLog() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
        try {
            Dialog d = new Dialog(this);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            logDialog = d;

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(0xCC000000); // semi-transparent black

            // Top bar with title and close button
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.setPadding(dp(4), dp(2), dp(4), dp(2));

            TextView title = new TextView(this);
            title.setText("LOG");
            title.setTextColor(0xFF00FF00);
            title.setTextSize(10);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            title.setLayoutParams(tLp);

            Button close = new Button(this);
            close.setText("X");
            close.setTextColor(Color.WHITE);
            close.setTextSize(10);
            close.setMinWidth(0);
            close.setMinHeight(0);
            close.setMinimumWidth(0);
            close.setMinimumHeight(0);
            close.setPadding(dp(8), dp(2), dp(8), dp(2));
            close.setBackgroundColor(0x88FF0000);
            close.setOnClickListener(v -> {
                try { d.dismiss(); } catch (Throwable ignored) {}
            });

            top.addView(title);
            top.addView(close);
            root.addView(top);

            logScrollView = new ScrollView(this);
            logTextView = new TextView(this);
            logTextView.setTextColor(0xFF00FF00);
            logTextView.setTextSize(9);
            logTextView.setTypeface(android.graphics.Typeface.MONOSPACE);
            logTextView.setPadding(dp(4), dp(4), dp(4), dp(4));
            logTextView.setTextIsSelectable(true);
            logTextView.setText("Menunggu log (PS2_RENDER/EGL/libEGL/AndroidRuntime)...\n");
            logScrollView.addView(logTextView);
            LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            logScrollView.setLayoutParams(svLp);
            root.addView(logScrollView);

            d.setContentView(root);
            d.setCanceledOnTouchOutside(false);

            Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams attrs = w.getAttributes();
                // Position at top-left, reasonable size (not full screen)
                attrs.gravity = Gravity.TOP | Gravity.START;
                attrs.x = dp(8);
                attrs.y = dp(8);
                attrs.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.55f);
                attrs.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.45f);
                // Keep flags minimal; do not use FLAG_NOT_FOCUSABLE so X/scroll work.
                // Do not use SYSTEM_ALERT_WINDOW or addView.
                w.setAttributes(attrs);
                w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }

            d.setOnShowListener(x -> { dialogShowing = true; startLogUpdater(); });
            d.setOnDismissListener(x -> { dialogShowing = false; stopLogUpdater(); logDialog = null; });
            d.show();
        } catch (Throwable t) {
            // If dialog fails (e.g. native window quirks), try once more later
            handler.postDelayed(this::showFloatingLog, 2000);
        }
    }

    private void startLogUpdater() {
        stopLogUpdater();
        logUpdater = new Runnable() {
            @Override public void run() {
                if (!dialogShowing) return;
                refreshLog();
                handler.postDelayed(this, 1500);
            }
        };
        handler.post(logUpdater);
    }

    private void stopLogUpdater() {
        if (logUpdater != null) {
            handler.removeCallbacks(logUpdater);
            logUpdater = null;
        }
    }

    private void refreshLog() {
        new Thread(() -> {
            String text = readFilteredLogcat();
            handler.post(() -> {
                if (logTextView != null && dialogShowing) {
                    logTextView.setText(text);
                    // auto-scroll to bottom
                    if (logScrollView != null) {
                        logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_DOWN));
                    }
                }
            });
        }, "logcat-reader").start();
    }

    private String readFilteredLogcat() {
        try {
            // Real-time filtered log: PS2_RENDER, EGL, libEGL, AndroidRuntime
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{
                    "logcat", "-d", "-t", "300",
                    "PS2_RENDER:*", "EGL:*", "libEGL:*", "AndroidRuntime:E", "*:S"
            });
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            r.close();
            p.waitFor();
            String s = sb.toString().trim();
            if (s.isEmpty()) {
                return "(Belum ada log PS2_RENDER/EGL/libEGL/AndroidRuntime)\n";
            }
            return s;
        } catch (Exception e) {
            return "Gagal baca log: " + e.getMessage();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        stopLogUpdater();
        try { if (logDialog != null && logDialog.isShowing()) logDialog.dismiss(); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
