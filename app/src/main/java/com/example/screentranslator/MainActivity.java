package com.example.screentranslator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1000;
    private static final int REQUEST_MEDIA_PROJECTION = 1001;

    private EditText editIp;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editIp = findViewById(R.id.editIp);
        Button btnStart = findViewById(R.id.btnStartOverlay);

        // Khởi tạo projection manager
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // Load IP đã lưu nếu có
        SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
        String savedIp = prefs.getString("server_ip", "");
        editIp.setText(savedIp);

        btnStart.setOnClickListener(v -> {
            String ip = editIp.getText().toString().trim();

            if (ip.isEmpty()) {
                editIp.setError("IP is required");
                return;
            }

            // Lưu IP
            prefs.edit().putString("server_ip", ip).apply();

            if (!Settings.canDrawOverlays(MainActivity.this)) {
                // Mở UI cấp quyền overlay
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            } else {
                requestMediaProjection(); // xin quyền media projection
            }
        });


        Button btnStop = findViewById(R.id.btnStop);
        btnStop.setOnClickListener(v -> {
            // Dừng service
            Intent stopIntent = new Intent(MainActivity.this, FloatingViewService.class);
            stopService(stopIntent);

            // Thoát toàn bộ ứng dụng
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask(); // Đóng và xoá khỏi recent apps
            } else {
                finish(); // Đóng activity
            }

            System.exit(0); // Đảm bảo dừng toàn bộ process (cẩn thận nếu có task khác)
        });
    }

    private void requestMediaProjection() {
        Intent screenCaptureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(screenCaptureIntent, REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        System.out.println("Start service with resultCode=" + resultCode);

        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                requestMediaProjection();
            } else {
                Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show();
            }
        }

        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            // Truyền MediaProjection vào service
            Intent serviceIntent = new Intent(this, FloatingViewService.class);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "Floating service started", Toast.LENGTH_SHORT).show();
        }
    }
}
