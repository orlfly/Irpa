package com.kingsware.irpa.automation;

import static android.app.Activity.RESULT_CANCELED;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Surface;


import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";

    //需要建立Notify通道，不然权限设置出错
    private static final int NOTIFICATION_ID = 123;
    private static final String CHANNEL_ID = "screen_capture";

    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;

    private final IBinder binder = new ScreenCaptureService.LocalBinder();

    public class LocalBinder extends Binder {
        public ScreenCaptureService getService() {
            return ScreenCaptureService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕捕获服务运行中")
                .setContentText("正在执行屏幕截图操作");

        builder.setChannelId(CHANNEL_ID);
        return builder.build();
    }
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
        );
        getSystemService(NotificationManager.class)
                .createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 获取从 Activity 传递的 MediaProjection 参数
        int resultCode = intent.getIntExtra("resultCode", RESULT_CANCELED);
        Intent data = intent.getParcelableExtra("data");


        // 初始化 MediaProjection
        MediaProjectionManager mpManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpManager.getMediaProjection(resultCode, data);

        // 开始屏幕捕获
        setupVirtualDisplay();

        return START_NOT_STICKY;
    }

    private void setupVirtualDisplay() {// 初始化屏幕参数
        DisplayMetrics metrics = getResources().getDisplayMetrics();

        imageReader = ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2
        );

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null
        );
    }

    public String screenshot() {
        String base64=null;
        if(imageReader!=null){
            // 获取屏幕图像
            Image image = imageReader.acquireLatestImage();
            if (image == null) return null;

            // 转换为 Bitmap
            Bitmap bitmap = processImage(image);
            base64 = encodeToBase64(bitmap);

            // 清理资源
            image.close();
            bitmap.recycle();
        }
        return base64;
    }

    private String encodeToBase64(Bitmap bitmap) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output);
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private Bitmap processImage(Image image) {
        // 获取图像数据
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();

        // 创建 Bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);

        return bitmap;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSelf();
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (mediaProjection != null) mediaProjection.stop();

    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
