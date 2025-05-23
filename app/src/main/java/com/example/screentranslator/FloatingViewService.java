package com.example.screentranslator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
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
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class FloatingViewService extends Service {

    private static final String CHANNEL_ID = "translator_channel";
    private static final int NOTIFICATION_ID = 1;

    private WindowManager windowManager;
    private Handler mainHandler;
    private RequestQueue requestQueue;
    private final Map<TextBlockInfo, TextView> overlayMap = new HashMap<>();
    private final int CAPTURE_INTERVAL_MS = 5000;
    private Bitmap previousBitmap = null;
    private View bubbleView;
    private boolean isPaused = false;



    private final Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPaused) {
                Log.d("Service", "Paused, skipping capture.");
                mainHandler.postDelayed(this, CAPTURE_INTERVAL_MS);
                return;
            }

            hideOverlaysTemporarily(() -> {
                ScreenshotHelper.capture((bitmap, width, height) -> {
                    if (bitmap != null) {
                        if (isBitmapDifferent(bitmap, previousBitmap)) {
                            previousBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                            Log.d("Capture", "Detected screen change, sending to server...");
                            sendImageToServer(bitmap, width, height);
                        } else {
                            Log.d("Capture", "Screen unchanged, skipping API call.");
                        }
                    } else {
                        Log.e("Capture", "Failed to capture screen (bitmap is null)");
                    }
                });
            });

            mainHandler.postDelayed(this, CAPTURE_INTERVAL_MS);
        }
    };




    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundServiceWithNotification();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        requestQueue = Volley.newRequestQueue(this);

        initFloatingBubble(); // <-- Thêm dòng này
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Service", "onStartCommand called");

        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");

            if (resultCode == -1 && data != null) {
                MediaProjectionManager projectionManager =
                        (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                MediaProjection mediaProjection = projectionManager.getMediaProjection(resultCode, data);

                if (mediaProjection != null) {
                    Log.d("Service", "Initializing ScreenshotHelper with MediaProjection");
                    ScreenshotHelper.init(this, mediaProjection);

                    // Bắt đầu chụp sau khi khởi tạo thành công
                    mainHandler.postDelayed(captureRunnable, 1000);
                } else {
                    Log.e("Service", "MediaProjection is null!");
                }
            } else {
                Log.e("Service", "Missing resultCode or data intent");
            }
        }

        return START_STICKY;
    }

    private void startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Translator",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Translator đang chạy")
                .setContentText("Đang dịch nội dung trên màn hình...")
                .setSmallIcon(R.drawable.notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void sendImageToServer(Bitmap bitmap, int bitmapWidth, int bitmapHeight) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
        String ip = prefs.getString("server_ip", null);
        if (ip == null) return;

        String url = "http://" + ip + ":5000/ocr_translate";

        JSONObject body = new JSONObject();
        try {
            body.put("image_base64", base64Image);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        JsonArrayPostRequest request = new JsonArrayPostRequest(
                Request.Method.POST, url, body,
                response -> {
                    try {
                        clearOverlays();

                        for (int i = 0; i < response.length(); i++) {
                            JSONObject item = response.getJSONObject(i);
                            String text = item.getString("translated");
                            int x = item.getInt("x");
                            int y = item.getInt("y");
                            int width = item.getInt("width");
                            int height = item.getInt("height");

                            showOverlayText(text, x, y, bitmapWidth, bitmapHeight);
                        }
                    } catch (Exception e) {
                        Log.e("API", "Error parsing translation result", e);
                    }
                },
                error -> Log.e("API", "Failed to get translation response", error)
        );

        requestQueue.add(request);

    }

    private void showOverlayText(String text, int left, int top, int bitmapWidth, int bitmapHeight) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(14);
        textView.setTextColor(0xFFFFFFFF);
        textView.setBackgroundColor(0xAA000000);
        textView.setPadding(20, 10, 20, 10);

        // Không dùng Resources.getSystem() nữa
        DisplayMetrics realMetrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(realMetrics);
        int screenWidth = realMetrics.widthPixels;
        int screenHeight = realMetrics.heightPixels;

        float scaleX = (float) screenWidth / bitmapWidth;
        float scaleY = (float) screenHeight / bitmapHeight;

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = wm.getCurrentWindowMetrics();
            WindowInsets insets = metrics.getWindowInsets();
            int statusBarHeight = insets.getInsets(WindowInsets.Type.statusBars()).top;
            params.y = adjustedY - statusBarHeight;
        }
//        else {
//            int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
//            int statusBarHeight = resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
//            params.y = adjustedY - statusBarHeight;
//        }

        try {
            windowManager.addView(textView, params);
            overlayMap.put(new TextBlockInfo(text, adjustedX, adjustedY), textView);
        } catch (Exception e) {
            Log.e("Overlay", "Failed to add overlay", e);
        }
    }


    private void clearOverlays() {
        for (TextView view : overlayMap.values()) {
            try {
                windowManager.removeView(view);
            } catch (Exception e) {
                Log.e("Overlay", "Failed to remove overlay", e);
            }
        }
        overlayMap.clear();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(captureRunnable);
        clearOverlays();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isBitmapDifferent(Bitmap current, Bitmap previous) {
        if (current == null || previous == null) return true;
        if (current.getWidth() != previous.getWidth() || current.getHeight() != previous.getHeight())
            return true;

        int width = current.getWidth();
        int height = current.getHeight();

        int[] pixelsCurrent = new int[width * height];
        int[] pixelsPrevious = new int[width * height];

        current.getPixels(pixelsCurrent, 0, width, 0, 0, width, height);
        previous.getPixels(pixelsPrevious, 0, width, 0, 0, width, height);

        int differentPixels = 0;
        for (int i = 0; i < pixelsCurrent.length; i++) {
            if (pixelsCurrent[i] != pixelsPrevious[i]) {
                differentPixels++;
                if (differentPixels > 1500) return true; // nếu có hơn 1000 pixel khác thì coi là khác
            }
        }

        return false;
    }

    private void hideOverlaysTemporarily(Runnable afterHidden) {
        for (TextView view : overlayMap.values()) {
            view.setAlpha(0f); // Làm trong suốt
        }

        if (bubbleView != null) {
            bubbleView.setAlpha(0f); // Ẩn bubble chính luôn
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            afterHidden.run(); // Gọi sau khi overlay đã bị ẩn
            for (TextView view : overlayMap.values()) {
                view.setAlpha(1f); // Hiện lại
            }
            if (bubbleView != null) {
                bubbleView.setAlpha(1f); // Hiện lại bubble
            }
        }, 500); // Delay ngắn để đảm bảo overlay đã bị ẩn khỏi ảnh chụp
    }

    private void initFloatingBubble() {
        bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;

        windowManager.addView(bubbleView, params);

        ImageView btnMain = bubbleView.findViewById(R.id.btnMain);
        LinearLayout menuButtons = bubbleView.findViewById(R.id.menuButtons);

        btnMain.setOnClickListener(v -> {
            if (menuButtons.getVisibility() == View.VISIBLE) {
                menuButtons.setVisibility(View.GONE);
            } else {
                menuButtons.setVisibility(View.VISIBLE);
            }
        });

        ImageButton btnPause = bubbleView.findViewById(R.id.Pause);
        btnPause.setOnClickListener(v -> {
            isPaused = !isPaused;

            if (isPaused) {
                Toast.makeText(this, "Stop Translate", Toast.LENGTH_SHORT).show();
                btnPause.setBackgroundResource(R.drawable.play); // icon phát lại
            } else {
                Toast.makeText(this, "Continue Tranlate", Toast.LENGTH_SHORT).show();
                btnPause.setBackgroundResource(R.drawable.pause); // icon tạm dừng
            }
        });


        bubbleView.findViewById(R.id.btnStop).setOnClickListener(v -> {
            // Dừng service
            stopSelf();

            // Gỡ bubble
            if (windowManager != null && bubbleView != null) {
                windowManager.removeView(bubbleView);
            }

            // Thoát app
            new Handler(Looper.getMainLooper()).postDelayed(() -> System.exit(0), 300);
        });
    }
}
