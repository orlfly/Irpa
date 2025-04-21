package com.kingsware.irpa;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.core.app.NotificationCompat;

import com.kingsware.irpa.zeromq.MessageDisplayer;
import com.kingsware.irpa.zeromq.ZeromqService;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private long lastClickTime = 0;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final MessageDisplayer messageDisplayer = new MessageDisplayer() {
        @Override
        public void displayMessage(String message) {
            Runnable task = ()->{
                updateMessage(message);
            };
            mHandler.post(task);
        }

        @Override
        public void displayMessage(String message, int duration) {
            Runnable task = ()->{
                updateMessage(message, duration*1000);
            };
            mHandler.post(task);
        }
    };
    private final ServiceConnection mqConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service){
            Log.i(Context.ACTIVITY_SERVICE, "Service Connected");
            String data=null;
            //通过IBinder获取Service句柄
            ZeromqService.LocalBinder binder=(ZeromqService.LocalBinder)service;
            ZeromqService mqService = binder.getService();
            mqService.setMessageDisplayer(messageDisplayer);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(Context.ACTIVITY_SERVICE, "Service Disconnected");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createFloatingWindow();
        startForeground(1, createNotification());

        Intent intent=new Intent(this, ZeromqService.class);
        bindService(intent,this.mqConnection,Context.BIND_AUTO_CREATE);
    }

    private void createFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_view, null);
        TextView tvMessage = floatingView.findViewById(R.id.tv_message);
        ImageView ivIcon = floatingView.findViewById(R.id.iv_icon);

        // 设置初始信息
        tvMessage.setText("你好,我是你的智能助手。");
        ivIcon.setImageResource(R.drawable.ic_robot);

        // 窗口参数设置
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        // 初始位置设为右上角
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 30;
        params.y = 100;
        // 添加触摸监听
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 双击检测
                        if (System.currentTimeMillis() - lastClickTime < 300) {
                            openMainPanel();
                            lastClickTime = 0;
                            return true;
                        }
                        lastClickTime = System.currentTimeMillis();

                        // 记录初始位置
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        // 更新位置
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
        floatingView.setAlpha(0f);
        floatingView.animate()
                .alpha(1f)
                .setDuration(500)
                .start();

        windowManager.addView(floatingView, params);

    }

    public void updateMessage(String newMessage) {
        if (floatingView != null) {
            TextView tv = floatingView.findViewById(R.id.tv_message);
            tv.setText(newMessage);

            // 添加更新动画
            tv.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(200)
                    .withEndAction(() -> tv.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .start())
                    .start();
        }
    }
    public void updateMessage(String newMessage, int duration) {
        if (floatingView != null) {
            TextView tv = floatingView.findViewById(R.id.tv_message);
            String oldMessage = tv.getText().toString();
            updateMessage(newMessage);
            Runnable task = ()->{
                updateMessage(oldMessage);
            };
            mHandler.postDelayed(task,duration);
        }
    }

    public void changeIcon(@DrawableRes int iconRes, @ColorInt int color) {
        if (floatingView != null) {
            ImageView iv = floatingView.findViewById(R.id.iv_icon);
            iv.setImageResource(iconRes);
            iv.setColorFilter(color);
        }
    }
    private void openMainPanel() {
        Intent intent = new Intent(this, MainPanelActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private Notification createNotification() {
        NotificationChannel channel = new NotificationChannel(
                "float_channel",
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
        );
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        return new NotificationCompat.Builder(this, "float_channel")
                .setContentTitle("悬浮窗服务运行中")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
        unbindService(mqConnection);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}