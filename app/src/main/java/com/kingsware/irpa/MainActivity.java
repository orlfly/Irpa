package com.kingsware.irpa;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.kingsware.irpa.automation.AutoAccessibilityService;
import com.kingsware.irpa.zeromq.ZeromqService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageView ivRobot = findViewById(R.id.iv_robot);
        Log.i(TAG, "main active start");
        // 设置机器人动画
        animateRobotIcon(ivRobot);

        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handlePermissionResult()
        );

        new Handler(Looper.getMainLooper()).postDelayed(this::checkOverlayPermission, 2000);
    }

    private void animateRobotIcon(ImageView ivRobot) {
        // 上下浮动动画
        ObjectAnimator floatAnim = ObjectAnimator.ofFloat(ivRobot, "translationY", 0f, 20f);
        floatAnim.setDuration(1500);
        floatAnim.setRepeatCount(ValueAnimator.INFINITE);
        floatAnim.setRepeatMode(ValueAnimator.REVERSE);
        floatAnim.start();

        // 眼睛发光效果
        ObjectAnimator scaleAnim = ObjectAnimator.ofPropertyValuesHolder(
                ivRobot,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.1f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.1f)
        );
        scaleAnim.setDuration(500);
        scaleAnim.setStartDelay(1000);
        scaleAnim.start();
    }

    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
        } else {
            startFloatingService();
        }
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        overlayPermissionLauncher.launch(intent);
    }

    private void handlePermissionResult() {
        if (Settings.canDrawOverlays(this)) {
            startFloatingService();
        } else {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void startFloatingService() {
        startService(new Intent(this, FloatingWindowService.class));
        finish();
    }
}