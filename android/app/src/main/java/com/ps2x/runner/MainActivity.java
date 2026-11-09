package com.ps2x.runner;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.io.*;

public class MainActivity extends Activity {
    private static final int REQ_PICK_ISO = 1001;
    private TextView tvIsoStatus;
    private SeekBar seekRes;
    private Spinner spinnerGraphics;
    private CheckBox cbInfHealth, cbInfChi;
    private Button btnStart;
    private Uri selectedIsoUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvIsoStatus = findViewById(R.id.tvIsoStatus);
        seekRes = findViewById(R.id.seekRes);
        spinnerGraphics = findViewById(R.id.spinnerGraphics);
        cbInfHealth = findViewById(R.id.cbInfHealth);
        cbInfChi = findViewById(R.id.cbInfChi);
        btnStart = findViewById(R.id.btnStart);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Low", "Medium", "High"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGraphics.setAdapter(adapter);

        findViewById(R.id.btnPickIso).setOnClickListener(v -> pickIso());
        btnStart.setOnClickListener(v -> startGame());
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
            copyIsoInBackground();
        }
    }

    private void copyIsoInBackground() {
        if (selectedIsoUri == null) return;
        tvIsoStatus.setText("Menyalin ISO...");
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(selectedIsoUri);
                 OutputStream out = new FileOutputStream(new File(getExternalFilesDir(null), "avatar.iso"))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                runOnUiThread(() -> tvIsoStatus.setText("ISO siap: avatar.iso"));
            } catch (Exception e) {
                runOnUiThread(() -> tvIsoStatus.setText("Gagal salin: " + e.getMessage()));
            }
        }).start();
    }

    private void startGame() {
        File iso = new File(getExternalFilesDir(null), "avatar.iso");
        if (!iso.exists()) {
            Toast.makeText(this, "Pilih & salin ISO dulu", Toast.LENGTH_SHORT).show();
            return;
        }
        float res = 0.5f + (seekRes.getProgress() * 0.5f);
        String gfx = spinnerGraphics.getSelectedItem().toString();
        boolean infHealth = cbInfHealth.isChecked();
        boolean infChi = cbInfChi.isChecked();

        SharedPreferences sp = getSharedPreferences("ps2cfg", MODE_PRIVATE);
        sp.edit()
            .putFloat("resolution", res)
            .putString("graphics", gfx)
            .putBoolean("infHealth", infHealth)
            .putBoolean("infChi", infChi)
            .apply();

        try {
            File cfg = new File(getExternalFilesDir(null), "config.ini");
            try (PrintWriter pw = new PrintWriter(cfg)) {
                pw.println("resolution=" + res);
                pw.println("graphics=" + gfx);
                pw.println("infHealth=" + infHealth);
                pw.println("infChi=" + infChi);
                pw.println("iso=" + iso.getAbsolutePath());
            }
        } catch (Exception ignored) {}

        Intent i = new Intent(this, android.app.NativeActivity.class);
        i.putExtra("iso_path", iso.getAbsolutePath());
        startActivity(i);
    }
}