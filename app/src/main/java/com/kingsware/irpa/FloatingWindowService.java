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
import android.os.Message;
import android.provider.Settings;
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

import com.kingsware.irpa.automation.AutoAccessibilityService;
import com.kingsware.irpa.zeromq.ZeromqService;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private long lastClickTime = 0;

    ZeromqService mqService;

    private Handler uiHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                Map<String,String> data = (Map<String,String>) msg.obj;
                String text =null;
                if(data.containsKey("text")){
                    text = (String)data.get("text");
                }
                if(data.containsKey("duration")){
                    int duration = Integer.parseInt(Objects.requireNonNull(data.get("duration")));
                    updateMessage(text, duration);
                } else {
                    updateMessage(text);
                }
            }
        }
    };
    public static class WeakReferenceHandler {
        private final WeakReference<Handler> mHandlerRef;

        public WeakReferenceHandler(Handler handler) {
            mHandlerRef = new WeakReference<>(handler);
        }

        public void sendMessage(Map<String,String> data) {
            Handler handler = mHandlerRef.get();
            if (handler != null) {
                Message msg = handler.obtainMessage(1, data);
                handler.sendMessage(msg);
            }
        }
    }
    private final ServiceConnection mqConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service){
            Log.i(Context.ACTIVITY_SERVICE, "Service Connected");
            String data=null;
            //通过IBinder获取Service句柄
            ZeromqService.LocalBinder binder=(ZeromqService.LocalBinder)service;
            mqService = binder.getService();
            boolean isEnabled = AutoAccessibilityService.isAccessibilityServiceEnabled(getApplicationContext(),AutoAccessibilityService.class);
            if (isEnabled) {
                Log.d("Accessibility", "服务已启用");
            } else {
                Log.d("Accessibility", "服务未启用");
                // 引导用户前往设置页面
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            AutoAccessibilityService autoAccessibilityService = AutoAccessibilityService.getInstance();
            mqService.setAutoAccessibilityService(autoAccessibilityService);
            mqService.setUIHandler(new WeakReferenceHandler(uiHandler));
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

        Intent intent=new Intent(this,ZeromqService.class);
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
            TimerTask durationTask = new TimerTask() {
                @Override
                public void run() {
                    Log.d(Context.ACTIVITY_SERVICE, "Change Message .......");
                    Map<String,String> msg = new HashMap<String,String>();
                    msg.put("text",oldMessage);
                    WeakReferenceHandler handle = new WeakReferenceHandler(uiHandler);
                    handle.sendMessage(msg);
                }
            };
            Timer timer = new Timer("duration");
            timer.schedule(durationTask,duration*1000);
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
        if(mqService != null) {
            mqService.clearUIHandler();
        }
        unbindService(mqConnection);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}