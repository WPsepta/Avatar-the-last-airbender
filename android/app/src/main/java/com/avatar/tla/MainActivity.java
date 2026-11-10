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
    private static final String ISO_NAME = "avatar.iso";
    private static final String[] RESOLUTIONS = {"144p", "240p", "360p", "480p", "720p", "1080p (HD)"};

    private TextView tvIsoStatus;
    private ProgressBar progressIso;
    private TextView tvProgress;
    private SeekBar seekRes;
    private TextView tvResLabel;
    private Spinner spinnerGraphics;
    private Button btnStart;
    private TextView tvError;

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
        findViewById(R.id.btnForceStop).setOnClickListener(v -> forceStopGame());

        // State persistence: check if ISO copied and ELF extracted
        File iso = new File(getExternalFilesDir(null), ISO_NAME);
        File elf = new File(getExternalFilesDir(null), ELF_NAME);
        if (iso.exists() && iso.length() > 0 && elf.exists() && elf.length() > 0) {
            tvIsoStatus.setText("Game Siap (ISO + " + ELF_NAME + ")");
            tvIsoStatus.setTextColor(0xFF00FF00);
            progressIso.setVisibility(View.GONE);
            tvProgress.setVisibility(View.GONE);
        } else if (elf.exists() && elf.length() > 0) {
            tvIsoStatus.setText("ELF ada, tapi avatar.iso belum disalin. Pilih ISO lagi.");
            tvIsoStatus.setTextColor(0xFFFFA000);
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
            copyIsoAndExtractElf();
        }
    }

    private void copyIsoAndExtractElf() {
        if (selectedIsoUri == null) return;
        progressIso.setVisibility(View.VISIBLE);
        progressIso.setProgress(0);
        tvProgress.setVisibility(View.VISIBLE);
        tvProgress.setText("0%");
        tvIsoStatus.setText("Menyalin ISO utuh (avatar.iso)... Ini bisa memakan waktu untuk file besar.");
        tvIsoStatus.setTextColor(0xFFAAAAAA);
        tvError.setVisibility(View.GONE);

        new Thread(() -> {
            File filesDir = getExternalFilesDir(null);
            File isoOut = new File(filesDir, ISO_NAME);
            File elfOut = new File(filesDir, ELF_NAME);
            try {
                if (filesDir == null) throw new IOException("Storage tidak tersedia");
                long totalSize = 0;
                try (android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(selectedIsoUri, "r")) {
                    if (pfd != null) totalSize = pfd.getStatSize();
                } catch (Exception ignored) {}

                // 1) Copy full ISO to avatar.iso (engine reads assets from this file)
                try (InputStream in = getContentResolver().openInputStream(selectedIsoUri);
                     OutputStream os = new FileOutputStream(isoOut)) {
                    if (in == null) throw new IOException("Tidak bisa membuka ISO");
                    byte[] buf = new byte[65536];
                    long copied = 0;
                    int n;
                    long lastUi = 0;
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        copied += n;
                        if (totalSize > 0) {
                            int pct = (int) Math.min(90, (copied * 90) / totalSize);
                            if (copied - lastUi > 1024 * 1024 || pct >= 90) {
                                lastUi = copied;
                                final int fp = pct;
                                final long fc = copied, ft = totalSize;
                                runOnUiThread(() -> {
                                    progressIso.setProgress(fp);
                                    tvProgress.setText(fp + "% (" + (fc / (1024 * 1024)) + "/" + (ft / (1024 * 1024)) + " MB)");
                                });
                            }
                        }
                    }
                    os.flush();
                }

                runOnUiThread(() -> {
                    progressIso.setProgress(92);
                    tvProgress.setText("92%");
                    tvIsoStatus.setText("ISO tersalin. Mengekstrak " + ELF_NAME + " untuk boot...");
                });

                // 2) Extract ELF from copied ISO for boot
                IsoExtractor.extractFileFromIsoFile(isoOut, elfOut, ELF_NAME, pct ->
                        runOnUiThread(() -> {
                            int mapped = 92 + (pct * 8) / 100;
                            progressIso.setProgress(mapped);
                            tvProgress.setText(mapped + "%");
                        }), 0, 100);
                // map callback inside gave 0-100, but we called with base 0; adjust final
                runOnUiThread(() -> {
                    progressIso.setProgress(100);
                    tvProgress.setText("100%");
                    progressIso.setVisibility(View.GONE);
                    tvProgress.setVisibility(View.GONE);
                    tvIsoStatus.setText("Game Siap: avatar.iso + " + ELF_NAME);
                    tvIsoStatus.setTextColor(0xFF00FF00);
                });
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                runOnUiThread(() -> {
                    progressIso.setVisibility(View.GONE);
                    tvProgress.setVisibility(View.GONE);
                    tvIsoStatus.setText("Gagal: " + msg);
                    tvIsoStatus.setTextColor(0xFFFF5252);
                    showError("Gagal salin/ekstrak: " + msg + "\nPastikan ruang penyimpanan cukup (~1.5GB untuk ISO).");
                });
            }
        }).start();
    }

    private void startGame() {
        tvError.setVisibility(View.GONE);
        File filesDir = getExternalFilesDir(null);
        File elf = new File(filesDir, ELF_NAME);
        File iso = new File(filesDir, ISO_NAME);
        if (!elf.exists() || elf.length() == 0) {
            showError("Pilih ISO dulu (ELF " + ELF_NAME + " belum diekstrak)");
            return;
        }
        if (!iso.exists() || iso.length() == 0) {
            showError("File avatar.iso (aset game) belum ada. Pilih ISO lagi agar disalin utuh ke " + (filesDir != null ? filesDir.getAbsolutePath() : "(storage)") + "/avatar.iso");
            return;
        }

        String res = RESOLUTIONS[seekRes.getProgress()];
        String gfx = spinnerGraphics.getSelectedItem().toString();

        SharedPreferences sp = getSharedPreferences("ps2cfg", MODE_PRIVATE);
        sp.edit().putString("resolution", res).putString("graphics", gfx).apply();

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
            return;
        } catch (Throwable e) {
            showError("Error JNI: " + e.toString());
            return;
        }

        try {
            Intent i = new Intent(this, GameActivity.class);
            i.putExtra("elf_path", elf.getAbsolutePath());
            startActivity(i);
        } catch (Exception e) {
            showError("Gagal memulai game: " + e.toString());
        }
    }

    private void forceStopGame() {
        try { finishAffinity(); } catch (Throwable ignored) {}
        try { android.os.Process.killProcess(android.os.Process.myPid()); } catch (Throwable ignored) {}
        try { System.exit(0); } catch (Throwable ignored) {}
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
}
