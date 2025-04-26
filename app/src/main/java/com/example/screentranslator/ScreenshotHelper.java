package com.example.screentranslator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.hardware.display.VirtualDisplay;

import java.nio.ByteBuffer;

public class ScreenshotHelper {

    private static ImageReader imageReader;
    private static VirtualDisplay virtualDisplay;
    private static boolean initialized = false;

    public interface ScreenshotCallback {
        void onScreenshot(Bitmap bitmap, int width, int height);
    }

    public static void init(Context context, MediaProjection mediaProjection) {
        if (initialized) {
            // clear old resources if re-init
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        }

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int dpi = metrics.densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                if (virtualDisplay != null) {
                    virtualDisplay.release();
                    virtualDisplay = null;
                }
                if (imageReader != null) {
                    imageReader.close();
                    imageReader = null;
                }
                initialized = false;
            }
        }, new Handler(Looper.getMainLooper()));

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                dpi,
                0,
                imageReader.getSurface(),
                null,
                null
        );

        initialized = true;
    }

    public static void capture(ScreenshotCallback callback) {
        if (!initialized || imageReader == null) {
            postResult(callback, null, 0, 0);
            return;
        }

        new Thread(() -> {
            Image image = null;
            int retries = 5;

            while (retries-- > 0 && image == null) {
                image = imageReader.acquireLatestImage();
                if (image == null) {
                    try {
                        Thread.sleep(100); // đợi thêm frame
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }

//            if (image != null) {
//                int width = image.getWidth();
//                int height = image.getHeight();
//                Image.Plane[] planes = image.getPlanes();
//                ByteBuffer buffer = planes[0].getBuffer();
//
//                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//                bitmap.copyPixelsFromBuffer(buffer);
//                image.close();
//
//                Log.d("Bitmap", "Captured bitmap size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
//
//                postResult(callback, bitmap, width, height);
//            } else {
//                postResult(callback, null, 0, 0);
//            }
            if (image != null) {
                int width = image.getWidth();
                int height = image.getHeight();
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride = planes[0].getPixelStride();
                int rowStride = planes[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;

                Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);

                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                image.close();

                Log.d("Bitmap", "Captured bitmap size: " + croppedBitmap.getWidth() + "x" + croppedBitmap.getHeight());

                postResult(callback, croppedBitmap, width, height);
            } else {
                postResult(callback, null, 0, 0);
            }

        }).start();
    }

    private static void postResult(ScreenshotCallback callback, Bitmap bitmap, int width, int height) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onScreenshot(bitmap, width, height));
    }
}
