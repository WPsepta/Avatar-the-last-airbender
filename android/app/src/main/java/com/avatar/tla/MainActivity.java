package com.avatar.tla;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import java.io.*;

public class MainActivity extends Activity {
    private static final int REQ_PICK_ISO = 1001;
    private static final String ELF_NAME = "SLUS_213.95";
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
    private static String libLoadError = null;

    static {
        try {
            System.loadLibrary("ps2EntryRunner");
        } catch (UnsatisfiedLinkError e) {
            libLoadError = e.toString();
        } catch (Throwable e) {
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
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvResLabel.setText(RESOLUTIONS[progress]);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
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

        // State persistence: check if ELF already extracted
        File elf = new File(getExternalFilesDir(null), ELF_NAME);
        if (elf.exists() && elf.length() > 0) {
            tvIsoStatus.setText("ELF siap: " + ELF_NAME + " (" + (elf.length() / 1024) + " KB)");
            progressIso.setVisibility(View.VISIBLE);
            progressIso.setProgress(100);
            tvProgress.setVisibility(View.VISIBLE);
            tvProgress.setText("100%");
        }

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
            extractElfFromIso();
        }
    }

    private void extractElfFromIso() {
        if (selectedIsoUri == null) return;
        progressIso.setVisibility(View.VISIBLE);
        progressIso.setProgress(0);
        tvProgress.setVisibility(View.VISIBLE);
        tvProgress.setText("0%");
        tvIsoStatus.setText("Mengekstrak " + ELF_NAME + " dari ISO...");
        tvError.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                long totalSize = 0;
                try (android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(selectedIsoUri, "r")) {
                    if (pfd != null) totalSize = pfd.getStatSize();
                } catch (Exception ignored) {}

                File outFile = new File(getExternalFilesDir(null), ELF_NAME);
                try (InputStream in = getContentResolver().openInputStream(selectedIsoUri)) {
                    if (in == null) throw new IOException("Tidak bisa membuka ISO");
                    IsoExtractor.extractFile(in, totalSize, outFile, ELF_NAME, pct ->
                            runOnUiThread(() -> {
                                progressIso.setProgress(pct);
                                tvProgress.setText(pct + "%");
                            })
                    );
                }

                final long size = outFile.length();
                runOnUiThread(() -> {
                    progressIso.setProgress(100);
                    tvProgress.setText("100%");
                    tvIsoStatus.setText("ELF siap: " + ELF_NAME + " (" + (size / 1024) + " KB)");
                });
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> {
                    progressIso.setVisibility(View.GONE);
                    tvProgress.setVisibility(View.GONE);
                    tvIsoStatus.setText("Gagal ekstrak ELF: " + msg);
                    showError("Ekstraksi gagal: " + msg);
                });
            }
        }).start();
    }

    private void startGame() {
        tvError.setVisibility(View.GONE);
        File elf = new File(getExternalFilesDir(null), ELF_NAME);
        if (!elf.exists() || elf.length() == 0) {
            showError("Pilih ISO dulu (ELF " + ELF_NAME + " belum diekstrak)");
            return;
        }

        String res = RESOLUTIONS[seekRes.getProgress()];
        String gfx = spinnerGraphics.getSelectedItem().toString();

        SharedPreferences sp = getSharedPreferences("ps2cfg", MODE_PRIVATE);
        sp.edit().putString("resolution", res).putString("graphics", gfx).apply();

        // Write config.ini with ELF path (engine may read this or use compile-time default)
        try {
            File cfg = new File(getExternalFilesDir(null), "config.ini");
            try (PrintWriter pw = new PrintWriter(cfg)) {
                pw.println("resolution=" + res);
                pw.println("graphics=" + gfx);
                pw.println("boot_elf=" + elf.getAbsolutePath());
            }
        } catch (Exception ignored) {}

        try {
            setResolution(res);
        } catch (UnsatisfiedLinkError e) {
            showError("Gagal memuat engine C++ (JNI): " + e.toString());
            bacaLog();
            return;
        } catch (Throwable e) {
            showError("Error JNI: " + e.toString());
            bacaLog();
            return;
        }

        try {
            Intent i = new Intent(this, GameActivity.class);
            i.putExtra("elf_path", elf.getAbsolutePath());
            startActivity(i);
        } catch (Exception e) {
            showError("Gagal memulai game: " + e.toString());
            bacaLog();
        }
    }

    private void bacaLog() {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{
                        "logcat", "-d", "-t", "500",
                        "PS2RecompJNI:*", "AndroidRuntime:E", "DEBUG:*", "libc:F", "*:S"
                });
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
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
        tvError.setVisibility(View.VISIBLE);
    }
}
