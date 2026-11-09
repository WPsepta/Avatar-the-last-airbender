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

    private TextView tvIsoStatus;
    private Spinner spinnerRes;
    private Spinner spinnerGraphics;
    private Button btnStart;

    private Uri selectedIsoUri = null;

    static {
        System.loadLibrary("ps2EntryRunner");
    }

    public native void setResolution(String res);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvIsoStatus = findViewById(R.id.tvIsoStatus);
        spinnerRes = findViewById(R.id.spinnerRes);
        spinnerGraphics = findViewById(R.id.spinnerGraphics);
        btnStart = findViewById(R.id.btnStart);

        ArrayAdapter<String> resAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"144p", "240p", "360p", "480p", "720p", "1080p (HD)"});
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRes.setAdapter(resAdapter);

        ArrayAdapter<String> gfxAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Low", "Medium", "High"});
        gfxAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGraphics.setAdapter(gfxAdapter);

        findViewById(R.id.btnPickIso).setOnClickListener(v -> pickIso());
        btnStart.setOnClickListener(v -> startGame());

        // Request overlay permission if not granted
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
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

        String res = spinnerRes.getSelectedItem().toString();
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

        // Call JNI
        setResolution(res);

        // Start CheatOverlayService
        startService(new Intent(this, CheatOverlayService.class));

        // Launch NativeActivity
        Intent i = new Intent(this, android.app.NativeActivity.class);
        i.putExtra("iso_path", iso.getAbsolutePath());
        startActivity(i);
    }
}