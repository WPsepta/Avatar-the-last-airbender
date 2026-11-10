package com.avatar.tla;

import android.app.Dialog;
import android.app.NativeActivity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class GameActivity extends NativeActivity {
    private Handler handler;
    private ImageButton gearButton;
    private boolean uiAdded = false;
    private SharedPreferences sp;

    // FPS
    private long lastFpsTime = 0;
    private int fpsFrames = 0;
    private float currentFps = 0f;

    // Dialog state
    private Dialog settingsDialog;
    private TextView fpsInfoView;
    private TextView logTextView;
    private Runnable dialogUpdater;
    private String currentTab = "Cheat";

    static {
        try { System.loadLibrary("ps2EntryRunner"); } catch (Throwable ignored) {}
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
        handler.postDelayed(this::tryAddGearButton, 800);
        startFpsCounter();
    }

    private void tryAddGearButton() {
        if (uiAdded || isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
        try {
            View decor = getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) { handler.postDelayed(this::tryAddGearButton, 500); return; }
            ViewGroup root = (ViewGroup) decor;

            ImageButton btn = new ImageButton(this);
            try {
                btn.setImageResource(R.drawable.ic_gear_button);
            } catch (Throwable t) {
                btn.setBackgroundColor(0x88000000);
            }
            btn.setBackgroundColor(0x55000000);
            btn.setPadding(dp(6), dp(6), dp(6), dp(6));
            btn.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            btn.setContentDescription("Settings");
            btn.setOnClickListener(v -> showSettingsDialog());

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(48), dp(48));
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = dp(8);
            lp.topMargin = dp(8);
            root.addView(btn, lp);
            gearButton = btn;
            uiAdded = true;
        } catch (Throwable t) {
            handler.postDelayed(this::tryAddGearButton, 700);
        }
    }

    private void startFpsCounter() {
        lastFpsTime = System.nanoTime();
        fpsFrames = 0;
        handler.post(new Runnable() {
            @Override public void run() {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                fpsFrames++;
                long now = System.nanoTime();
                long elapsed = now - lastFpsTime;
                if (elapsed >= 1_000_000_000L) {
                    currentFps = fpsFrames * 1_000_000_000f / elapsed;
                    fpsFrames = 0;
                    lastFpsTime = now;
                }
                handler.postDelayed(this, 200);
            }
        });
    }

    private void showSettingsDialog() {
        if (settingsDialog != null && settingsDialog.isShowing()) return;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        settingsDialog = dialog;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xCC1A1A2E);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);

        // Top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("PENGATURAN AVATAR PS2 RECOMP");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleLp);

        Button backBtn = new Button(this);
        backBtn.setText("Menu");
        backBtn.setAllCaps(false);
        backBtn.setTextColor(Color.WHITE);
        backBtn.setBackgroundColor(0xFF444444);
        backBtn.setOnClickListener(v -> {
            dialog.dismiss();
            try {
                startActivity(new android.content.Intent(GameActivity.this, MainActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP));
            } catch (Throwable ignored) {}
            finish();
        });

        Button closeBtn = new Button(this);
        closeBtn.setText("X");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setBackgroundColor(0x44FF0000);
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        topBar.addView(title);
        topBar.addView(backBtn);
        topBar.addView(closeBtn);
        root.addView(topBar);

        // Tabs
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setPadding(0, dp(8), 0, dp(8));
        String[] tabs = {"Cheat", "Tata Letak", "FPS & Info", "Log"};
        final Button[] tabButtons = new Button[tabs.length];
        final FrameLayout contentArea = new FrameLayout(this);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentArea.setLayoutParams(contentLp);

        for (int i = 0; i < tabs.length; i++) {
            final String tab = tabs[i];
            Button b = new Button(this);
            b.setText(tab);
            b.setAllCaps(false);
            b.setTextColor(Color.WHITE);
            b.setTextSize(12);
            b.setPadding(dp(6), dp(4), dp(6), dp(4));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blp.setMargins(dp(2), 0, dp(2), 0);
            b.setLayoutParams(blp);
            b.setOnClickListener(v -> {
                currentTab = tab;
                updateTabStyles(tabButtons, tabs);
                showTabContent(contentArea, tab);
            });
            tabButtons[i] = b;
            tabBar.addView(b);
        }
        root.addView(tabBar);
        root.addView(contentArea);

        updateTabStyles(tabButtons, tabs);
        currentTab = "Cheat";
        showTabContent(contentArea, "Cheat");

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = w.getAttributes();
            // landscape dialog with 16dp margin from edges
            int margin = dp(16);
            attrs.width = getResources().getDisplayMetrics().widthPixels - margin * 2;
            attrs.height = getResources().getDisplayMetrics().heightPixels - margin * 2;
            attrs.gravity = Gravity.CENTER;
            w.setAttributes(attrs);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        dialog.setOnDismissListener(d -> {
            if (dialogUpdater != null) handler.removeCallbacks(dialogUpdater);
            dialogUpdater = null;
            fpsInfoView = null;
            logTextView = null;
            settingsDialog = null;
        });

        dialog.show();
        startDialogUpdater();
    }

    private void updateTabStyles(Button[] btns, String[] tabs) {
        for (int i = 0; i < btns.length; i++) {
            boolean sel = tabs[i].equals(currentTab);
            btns[i].setBackgroundColor(sel ? 0xFF00796B : 0xFF333333);
        }
    }

    private void showTabContent(FrameLayout container, String tab) {
        container.removeAllViews();
        fpsInfoView = null;
        logTextView = null;
        if ("Cheat".equals(tab)) container.addView(buildCheatTab());
        else if ("Tata Letak".equals(tab)) container.addView(buildLayoutTab());
        else if ("FPS & Info".equals(tab)) container.addView(buildFpsTab());
        else if ("Log".equals(tab)) container.addView(buildLogTab());
    }

    private View buildCheatTab() {
        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(8), dp(8), dp(8), dp(8));
        String[] cheats = {"Infinite Health", "Infinite Chi", "Infinite Copper",
                "Infinite Stealth", "Double Damage", "One Hit Dishonor"};
        for (String cheat : cheats) {
            CheckBox cb = new CheckBox(this);
            cb.setText(cheat);
            cb.setTextColor(Color.WHITE);
            cb.setChecked(sp.getBoolean(cheat, false));
            cb.setOnCheckedChangeListener((bv, checked) -> {
                sp.edit().putBoolean(cheat, checked).apply();
                try { setCheatState(cheat, checked); } catch (Throwable ignored) {}
            });
            ll.addView(cb);
        }
        sv.addView(ll);
        return sv;
    }

    private View buildLayoutTab() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(8), dp(8), dp(8), dp(8));
        TextView info = new TextView(this);
        info.setText("Atur posisi tombol virtual (placeholder).\nFitur drag-and-drop akan ditambahkan nanti.");
        info.setTextColor(0xFFCCCCCC);
        ll.addView(info);
        return ll;
    }

    private View buildFpsTab() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(8), dp(8), dp(8), dp(8));
        fpsInfoView = new TextView(this);
        fpsInfoView.setTextColor(Color.WHITE);
        fpsInfoView.setTextSize(14);
        updateFpsInfo();
        ll.addView(fpsInfoView);
        return ll;
    }

    private View buildLogTab() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(this);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sv.setLayoutParams(svLp);
        sv.setFillViewport(true);
        logTextView = new TextView(this);
        logTextView.setTextColor(0xFF00FF00);
        logTextView.setTextSize(10);
        logTextView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logTextView.setPadding(dp(6), dp(6), dp(6), dp(6));
        logTextView.setText("Memuat log...");
        sv.addView(logTextView);
        ll.addView(sv);
        refreshLog();
        return ll;
    }

    private void updateFpsInfo() {
        if (fpsInfoView == null) return;
        String info = "FPS: " + String.format("%.1f", currentFps) + "\n"
                + "Device: " + Build.MODEL + " (" + Build.DEVICE + ")\n"
                + "Manufacturer: " + Build.MANUFACTURER + "\n"
                + "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "ABI: " + Build.SUPPORTED_ABIS[0];
        fpsInfoView.setText(info);
    }

    private void refreshLog() {
        final TextView target = logTextView;
        if (target == null) return;
        new Thread(() -> {
            String out = readFilteredLog();
            handler.post(() -> { if (logTextView == target) logTextView.setText(out); });
        }).start();
    }

    private String readFilteredLog() {
        try {
            // Focused filters for purple screen / GL issues as requested
            Process p = Runtime.getRuntime().exec(new String[]{
                    "logcat", "-d", "-t", "400",
                    "GL_DIAGNOSTIC:*", "libEGL:*", "OpenGLRenderer:*", "vulkan:*",
                    "AndroidRuntime:E", "DEBUG:*", "PS2RecompJNI:*", "*:S"
            });
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            p.waitFor();
            String s = sb.toString().trim();
            return s.isEmpty() ? "(Tidak ada log yang cocok dengan filter GL/EGL/Vulkan/Runtime)" : s;
        } catch (Exception e) {
            return "Gagal baca log: " + e.getMessage();
        }
    }

    private void startDialogUpdater() {
        if (dialogUpdater != null) handler.removeCallbacks(dialogUpdater);
        dialogUpdater = new Runnable() {
            @Override public void run() {
                if (settingsDialog == null || !settingsDialog.isShowing()) return;
                if ("FPS & Info".equals(currentTab)) updateFpsInfo();
                if ("Log".equals(currentTab)) refreshLog();
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(dialogUpdater, 1000);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dialogUpdater != null) handler.removeCallbacks(dialogUpdater);
        if (settingsDialog != null && settingsDialog.isShowing()) settingsDialog.dismiss();
        try {
            if (gearButton != null && gearButton.getParent() instanceof ViewGroup) {
                ((ViewGroup) gearButton.getParent()).removeView(gearButton);
            }
        } catch (Throwable ignored) {}
    }
}
