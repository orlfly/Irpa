package com.kingsware.irpa.automation;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import com.kingsware.irpa.zeromq.ZeromqService;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoAccessibilityService extends AccessibilityService {
    private static final String TAG = "AutoAccessibilityService";
    private static final long STABILITY_DELAY = 100;  // 200ms无变化视为稳定

    private static AutoAccessibilityService instance;

    private final ServiceConnection mqConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service){
            Log.i(TAG, "Service Connected");
            String data=null;
            //通过IBinder获取Service句柄
            ZeromqService.LocalBinder binder=(ZeromqService.LocalBinder)service;
            ZeromqService mqService = binder.getService();
            AutoAccessibilityService autoAccessibilityService = AutoAccessibilityService.getInstance();
            mqService.setAutoAccessibilityService(autoAccessibilityService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "Service Disconnected");
        }
    };

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Set<Integer> updatedNode;
    private long updateTime;
    private boolean mIsMonitoring;
    private Runnable mUpdatingCheck;

    public static AutoAccessibilityService getInstance() {
        return instance;
    }

    public void launchApp(String packageName) {
        Log.i(TAG, "start app: "+packageName);
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        Log.i(TAG, "start intent: "+intent);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }
    public void launchActivity(String packageName, String activityName) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    packageName,
                    activityName
            ));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("TAG", "启动失败: " + e.getMessage());
        }
    }
    public void stopApp(String packageName) {
        Log.i(TAG, "stop app: " + packageName);
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            activityManager.killBackgroundProcesses(packageName);
        }
    }




    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Accessibility Service Created");
        instance = this;

        Intent intent=new Intent(this, ZeromqService.class);
        bindService(intent,this.mqConnection,Context.BIND_AUTO_CREATE);

        mUpdatingCheck = () -> {
            if (mIsMonitoring) {
                if(Instant.now().toEpochMilli()-updateTime>STABILITY_DELAY) {
                    Log.d(TAG, "界面稳定性检测超时，强制判定加载完成");
                    onPageLoaded();
                } else {
                    mHandler.postDelayed(mUpdatingCheck, STABILITY_DELAY);
                }
            }
        };

    }


    public static boolean isAccessibilityServiceEnabled(Context context, Class<?extends AccessibilityService> serviceClass) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (enabledServices != null) {
            for (AccessibilityServiceInfo service : enabledServices) {
                if (service.getId().endsWith(serviceClass.getSimpleName())) {
                    return true;
                }
            }
        }
        return false;
    }
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                Log.d(TAG, "Window stat change:"+event.getWindowId()+":"+event.getPackageName()+":"+event.getClassName()+":"+event.getCurrentItemIndex());
                handleContentChanged(event);
                break;

            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                if(event.getSource()!=null){
                    Log.d(TAG, "Window content change:"+event.getWindowId()+":"+event.getPackageName()+":"+event.getClassName()+":"+ event.getSource());
                    handleContentChanged(event);
                }
                break;
        }
    }

    public void startLoadMonitoring() {
        mIsMonitoring = true;
        updateTime = Instant.now().toEpochMilli();
        updatedNode = new HashSet<>();
        mHandler.postDelayed(mUpdatingCheck, STABILITY_DELAY);
    }
    private void handleContentChanged(AccessibilityEvent event) {
        if (mIsMonitoring) {
            int sourceObjId = System.identityHashCode(event.getSource());
            if(!updatedNode.contains(sourceObjId)) {
                updatedNode.add(sourceObjId);
                updateTime = Instant.now().toEpochMilli();
                Log.i(TAG, "界面内容更新，重新计时");
            }
        }
    }

    private void onPageLoaded() {
        mIsMonitoring = false;
        Log.i("PageLoad", "界面加载完成.");

        // 执行后续操作...
        analyzePageStructure();
    }

    // 遍历界面结构（示例）
    private void analyzePageStructure() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        traverseNodes(root, 0);
        root.recycle();
    }

    private void traverseNodes(AccessibilityNodeInfo node, int depth) {
        if (node == null) return;

        // 获取节点信息
        String resId = node.getViewIdResourceName();
        CharSequence text = node.getText();

        // 输出节点信息
        Log.d("PageAnalyze",
                "[" + depth + "] " + node.getClassName() +
                        " ID:" + resId +
                        " Text:" + text);

        // 递归遍历子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseNodes(child, depth + 1);
                child.recycle();
            }
        }
    }
    private AccessibilityNodeInfo findViewByID(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        return !nodes.isEmpty() ? nodes.get(0) : null;
    }
    @Override
    public void onInterrupt() {
        // 当服务被中断时调用
        Log.d(TAG, "Accessibility Service Interrupted");
    }
}
