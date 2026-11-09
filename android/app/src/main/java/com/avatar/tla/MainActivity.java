package com.avatar.tla;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    private static final int REQ_PICK_ISO = 1001;
    private static final int REQ_OVERLAY = 1002;

    private static final String[] RESOLUTIONS = {"144p", "240p", "360p", "480p", "720p", "1080p (HD)"};

    private TextView tvIsoStatus;
    private ProgressBar progressIso;
    private TextView tvProgress;
    private SeekBar seekRes;
    private TextView tvResLabel;
    private Spinner spinnerGraphics;
    private Button btnStart;
    private TextView tvError;
    private TextView tvLog;

    private Uri selectedIsoUri = null;
    private boolean libLoaded = false;
    private static String libLoadError = null;

    static {
        try {
            System.loadLibrary("ps2EntryRunner");
        } catch (UnsatisfiedLinkError e) {
            libLoadError = e.toString();
        } catch (Exception e) {
            libLoadError = e.toString();
        }
    }

    public native void setResolution(String res);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvIsoStatus = findViewById(R.id.tvIsoStatus);
        progressIso = findViewById(R.id.progressIso);
        tvProgress = findViewById(R.id.tvProgress);
        seekRes = findViewById(R.id.seekRes);
        tvResLabel = findViewById(R.id.tvResLabel);
        spinnerGraphics = findViewById(R.id.spinnerGraphics);
        btnStart = findViewById(R.id.btnStart);
        tvError = findViewById(R.id.tvError);
        tvLog = findViewById(R.id.tvLog);

        seekRes.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvResLabel.setText(RESOLUTIONS[progress]);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        tvResLabel.setText(RESOLUTIONS[seekRes.getProgress()]);

        ArrayAdapter<String> gfxAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Low", "Medium", "High"});
        gfxAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGraphics.setAdapter(gfxAdapter);

        findViewById(R.id.btnPickIso).setOnClickListener(v -> pickIso());
        btnStart.setOnClickListener(v -> startGame());
        findViewById(R.id.btnReadLog).setOnClickListener(v -> bacaLog());

        // Check if avatar.iso already exists
        File iso = new File(getExternalFilesDir(null), "avatar.iso");
        if (iso.exists() && iso.length() > 0) {
            tvIsoStatus.setText("ISO siap: avatar.iso");
            progressIso.setProgress(100);
            progressIso.setVisibility(android.view.View.VISIBLE);
            tvProgress.setText("100%");
            tvProgress.setVisibility(android.view.View.VISIBLE);
        }

        // Request overlay permission if not granted
        if (!Settings.canDrawOverlays(this)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_OVERLAY);
            } catch (Exception e) {
                showError("Gagal minta izin overlay: " + e.getMessage());
            }
        }

        // Show library load error if any
        if (libLoadError != null) {
            showError("Gagal load library C++: " + libLoadError);
        }
    }

    private void pickIso() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PICK_ISO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_ISO && resultCode == RESULT_OK && data != null) {
            selectedIsoUri = data.getData();
            String name = (selectedIsoUri != null) ? selectedIsoUri.getLastPathSegment() : "file";
            tvIsoStatus.setText("Dipilih: " + name);
            copyIsoWithProgress();
        }
    }

    private void copyIsoWithProgress() {
        if (selectedIsoUri == null) return;
        progressIso.setVisibility(android.view.View.VISIBLE);
        tvProgress.setVisibility(android.view.View.VISIBLE);
        tvIsoStatus.setText("Menyalin ISO...");

        new Thread(() -> {
            try {
                Uri uri = selectedIsoUri;
                long totalSize = 0;
                try (android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
                    if (pfd != null) totalSize = pfd.getStatSize();
                } catch (Exception ignored) {}

                InputStream in = getContentResolver().openInputStream(uri);
                File outFile = new File(getExternalFilesDir(null), "avatar.iso");
                OutputStream out = new FileOutputStream(outFile);

                byte[] buf = new byte[65536];
                long copied = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    copied += n;
                    if (totalSize > 0) {
                        final int pct = (int) ((copied * 100) / totalSize);
                        runOnUiThread(() -> {
                            progressIso.setProgress(pct);
                            tvProgress.setText(pct + "%");
                        });
                    }
                }
                in.close();
                out.close();

                runOnUiThread(() -> {
                    progressIso.setProgress(100);
                    tvProgress.setText("100%");
                    tvIsoStatus.setText("ISO siap: avatar.iso");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressIso.setVisibility(android.view.View.GONE);
                    tvProgress.setVisibility(android.view.View.GONE);
                    tvIsoStatus.setText("Gagal salin: " + e.getMessage());
                });
            }
        }).start();
    }

    private void startGame() {
        tvError.setVisibility(android.view.View.GONE);
        File iso = new File(getExternalFilesDir(null), "avatar.iso");
        if (!iso.exists()) {
            showError("Pilih & salin ISO dulu");
            return;
        }

        String res = RESOLUTIONS[seekRes.getProgress()];
        String gfx = spinnerGraphics.getSelectedItem().toString();

        SharedPreferences sp = getSharedPreferences("ps2cfg", MODE_PRIVATE);
        sp.edit()
            .putString("resolution", res)
            .putString("graphics", gfx)
            .apply();

        // Write config.ini
        try {
            File cfg = new File(getExternalFilesDir(null), "config.ini");
            try (PrintWriter pw = new PrintWriter(cfg)) {
                pw.println("resolution=" + res);
                pw.println("graphics=" + gfx);
                pw.println("iso=" + iso.getAbsolutePath());
            }
        } catch (Exception ignored) {}

        // Call JNI setResolution with try-catch
        try {
            setResolution(res);
        } catch (UnsatisfiedLinkError e) {
            showError("Gagal memuat engine C++ (JNI): " + e.toString());
            return;
        } catch (Throwable e) {
            showError("Error JNI: " + e.toString());
            return;
        }

        // Start GameOverlayService
        try {
            startService(new Intent(this, GameOverlayService.class));
        } catch (Exception e) {
            showError("Gagal start overlay: " + e.getMessage());
        }

        // Launch GameActivity with try-catch
        try {
            Intent i = new Intent(this, GameActivity.class);
            i.putExtra("iso_path", iso.getAbsolutePath());
            startActivity(i);
        } catch (Exception e) {
            showError("Gagal memuat engine C++: " + e.toString());
            bacaLog();
        }
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
                    if (tvLog != null) {
                        tvLog.setText(logOutput.isEmpty() ? "Tidak ada log" : logOutput);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (tvLog != null) {
                        tvLog.setText("Gagal baca log: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(android.view.View.VISIBLE);
    }
}