package com.kingsware.irpa.automation;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Objects;

public class AutoAccessibilityService extends AccessibilityService {
    private static final String TAG = "AutoAccessibilityService";
    private static final long LOADING_TIMEOUT = 3000; // 3秒超时
    private static final long STABILITY_DELAY = 1000;  // 1000ms无变化视为稳定

    private static AutoAccessibilityService instance;

    private Handler mHandler = new Handler();
    private String mLastPackage;
    private Runnable mStabilityCheck;
    private boolean mIsMonitoring;

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
                handleWindowChanged(event);
                break;

            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                if(event.getSource()!=null){
                    Log.d(TAG, "Window content change:"+event.getWindowId()+":"+event.getPackageName()+":"+event.getClassName()+":"+ event.getSource());
                }
                break;
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                Log.d(TAG, "View Scrolled:"+event);
                handleContentChanged(event);
                break;
        }
    }

    private void handleWindowChanged(AccessibilityEvent event) {
        String packageName = String.valueOf(event.getPackageName());
        if (!packageName.equals(mLastPackage)) {
            mLastPackage = packageName;
            startLoadMonitoring();
        }
    }

    private void startLoadMonitoring() {
        mIsMonitoring = true;
        mHandler.removeCallbacks(mStabilityCheck);

        mHandler.postDelayed(() -> {
            if (mIsMonitoring) {
                if (checkContentLoaded()) {
                    onPageLoaded();
                } else {
                    // 超时强制判定
                    mHandler.postDelayed(this::onPageLoaded, LOADING_TIMEOUT);
                }
            }
        }, STABILITY_DELAY);
    }
    private void handleContentChanged(AccessibilityEvent event) {
        if (mIsMonitoring) {
            // 重置稳定性检测计时器
            mHandler.removeCallbacks(mStabilityCheck);
            mStabilityCheck = () -> {
                if (checkContentLoaded()) {
                    onPageLoaded();
                }
            };
            mHandler.postDelayed(mStabilityCheck, STABILITY_DELAY);
        }
    }

    // 布局稳定性检测
    private boolean checkContentLoaded() {
        // 实现布局差异对比（示例使用简单计数法）
        AccessibilityNodeInfo root = getRootInActiveWindow();
        int childCount = root != null ? root.getChildCount() : 0;
        if(root != null) {
            root.recycle();
        }
        // 实际项目应记录布局特征进行对比
        return childCount > 0; // 简化的稳定性判断
    }

    private void onPageLoaded() {
        mIsMonitoring = false;
        Log.d("PageLoad", "界面加载完成：" + mLastPackage);

        // 执行后续操作...
        //analyzePageStructure();
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
