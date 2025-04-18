package com.example.screentranslator;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.util.DisplayMetrics;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FloatingViewService extends Service {

    private MediaProjection mediaProjection;
    private WindowManager windowManager;
    private RequestQueue requestQueue;
    private Handler handler = new Handler();

    private final Set<TextBlockInfo> lastCapturedBlocks = new HashSet<>();
    private final Map<TextBlockInfo, TextView> overlayMap = new HashMap<>();

    private static class TextBlockInfo {
        String text;
        int approxX, approxY;

        public TextBlockInfo(String text, int x, int y) {
            this.text = text;
            this.approxX = x / 10;
            this.approxY = y / 10;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TextBlockInfo)) return false;
            TextBlockInfo other = (TextBlockInfo) obj;
            return text.equals(other.text) && approxX == other.approxX && approxY == other.approxY;
        }

        @Override
        public int hashCode() {
            return text.hashCode() + approxX * 31 + approxY * 17;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        requestQueue = Volley.newRequestQueue(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "screen_translator_channel";
            String channelName = "Screen Translator";
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, channelId)
                    .setContentTitle("Screen Translator đang chạy")
                    .setContentText("Đang dịch văn bản trên màn hình")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();

            startForeground(1, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");

        if (resultCode == -1 && data != null) {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = mpm.getMediaProjection(resultCode, data);

            ScreenshotHelper.init(this, mediaProjection);

            handler.postDelayed(captureRunnable, 1000);
        }

        return START_NOT_STICKY;
    }

    private final Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            ScreenshotHelper.capture((bitmap, width, height) -> {
                if (bitmap != null) {
                    recognizeText(bitmap, width, height);
                }
            });
            handler.postDelayed(this, 5000);
        }
    };

    private void recognizeText(Bitmap bitmap, int bitmapWidth, int bitmapHeight) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener(result -> {
                    Set<TextBlockInfo> currentBlocks = new HashSet<>();

                    for (Text.TextBlock block : result.getTextBlocks()) {
                        String originalText = block.getText().trim();
                        int top = block.getBoundingBox().top;
                        int left = block.getBoundingBox().left;

                        TextBlockInfo info = new TextBlockInfo(originalText, left, top);
                        currentBlocks.add(info);

                        if (!overlayMap.containsKey(info)) {
                            translateAndShow(originalText, top, left, bitmapWidth, bitmapHeight, info);
                        }
                    }

                    for (TextBlockInfo oldInfo : new HashSet<>(overlayMap.keySet())) {
                        if (!currentBlocks.contains(oldInfo)) {
                            TextView view = overlayMap.remove(oldInfo);
                            windowManager.removeView(view);
                        }
                    }

                    lastCapturedBlocks.clear();
                    lastCapturedBlocks.addAll(currentBlocks);
                })
                .addOnFailureListener(e -> Log.e("OCR", "Text recognition failed", e));
    }


    private void translateAndShow(String originalText, int top, int left, int bitmapWidth, int bitmapHeight, TextBlockInfo info) {
        try {
            SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
            String ip = prefs.getString("server_ip", null);
            if (ip == null) {
                showOverlayText("IP not set", top, left, bitmapWidth, bitmapHeight, info);
                return;
            }

            String url = "http://" + ip + ":5000/translate";
            JSONObject body = new JSONObject();
            body.put("text", originalText);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                    response -> {
                        try {
                            String translated = response.getString("translated");
                            showOverlayText(translated, top, left, bitmapWidth, bitmapHeight, info);
                        } catch (Exception e) {
                            showOverlayText("Error", top, left, bitmapWidth, bitmapHeight, info);
                        }
                    },
                    error -> showOverlayText("Fail", top, left, bitmapWidth, bitmapHeight, info)
            ) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    return headers;
                }
            };

            requestQueue.add(request);
        } catch (Exception e) {
            showOverlayText("Err", top, left, bitmapWidth, bitmapHeight, info);
        }
    }

    private void showOverlayText(String text, int top, int left, int bitmapWidth, int bitmapHeight, TextBlockInfo info) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(14);
        textView.setTextColor(0xFFFFFFFF);
        textView.setBackgroundColor(0xAA000000);
        textView.setPadding(20, 10, 20, 10);

        // Lấy screen size thật (không dùng windowManager nữa)
        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        // Tính scale
        float scaleX = (float) screenWidth / bitmapWidth;
        float scaleY = (float) screenHeight / bitmapHeight;

        // Áp scale vào vị trí gốc
        int adjustedX = Math.round(left * scaleX);
        int adjustedY = Math.round(top * scaleY);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = adjustedX;
        params.y = adjustedY;

        try {
            windowManager.addView(textView, params);
            overlayMap.put(info, textView);
        } catch (Exception e) {
            Log.e("Overlay", "Failed to add overlay", e);
        }
    }



    private void clearOverlays() {
        for (TextView view : overlayMap.values()) {
            windowManager.removeView(view);
        }
        overlayMap.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        clearOverlays();
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
