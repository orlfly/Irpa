package com.kingsware.irpa.automation;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import com.kingsware.irpa.zeromq.ZeromqService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoAccessibilityService extends AccessibilityService {
    private static final String TAG = "AutoAccessibilityService";
    private static final long STABILITY_DELAY = 100;  // 200ms无变化视为稳定
    private static final int SWIPE_TYPE_RIGHT = 1;
    private static final int SWIPE_TYPE_LEFT = 2;
    private static final int SWIPE_TYPE_UP = 3;
    private static final int SWIPE_TYPE_DOWN = 4;

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
    public void click(Rect rect) {
        AccessibilityNodeInfo node = findNode(rect);
        if(node!=null){
            Log.d(TAG, "click: "+node.getText());
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } else {
            Log.d(TAG, "click: "+rect.toString());
            int centerX = rect.left + (rect.width() / 2);
            int centerY = rect.top + (rect.height() / 2);
            performClick(centerX, centerY, 50);
        }
    }
    public void swipe(int type) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int height = metrics.heightPixels/4;
        int width = metrics.widthPixels/4;
        Log.d(TAG, "swipe: "+type +" position:"+width+":"+height);
        switch(type){
            case SWIPE_TYPE_RIGHT:
                performSwipe(width, height*2, width*3, height*2);
                break;
            case SWIPE_TYPE_LEFT:
                performSwipe(width*3, height*2, width, height*2);
                break;
            case SWIPE_TYPE_UP:
                performSwipe(width*2, height*3, width, height);
                break;
            case SWIPE_TYPE_DOWN:
                performSwipe(width*2, height, width, height*3);
                break;
            default:
                Log.d(TAG, "Invalid swipe type: " + type);
                break;
        }
    }

    /**
     * 带持续时间的点击（可用于长按）
     * @param x X坐标
     * @param y Y坐标
     * @param duration 点击持续时间(ms)
     */
    private void performClick(int x, int y, int duration) {
        Path clickPath = new Path();
        clickPath.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(
                        clickPath, 0, duration);

        dispatchGesture(
                new GestureDescription.Builder()
                        .addStroke(stroke)
                        .build(),
                null,
                null);
    }

    private void performSwipe(int startX, int startY, int endX, int endY) {
        // 创建手势路径
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);
        Log.i(TAG,"swipe:"+swipePath);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(
                swipePath, 0, 50)); // 500ms完成滑动

        boolean ret = dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d(TAG, "Gesture executed successfully");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.e(TAG, "Gesture cancelled");
            }
        }, null);
        Log.i(TAG,"Gesture exec:"+ret);
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
    @Override
    public void onServiceConnected() {
        // 配置监听的事件类型
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_GESTURE_DETECTION_END
                | AccessibilityEvent.TYPE_VIEW_SCROLLED;

        // 指定可以处理的包名（null表示所有应用）
        info.packageNames = null;

        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        setServiceInfo(info);
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
                Log.d(TAG, "Window stat change.");
                handleContentChanged(event);
                break;

            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                if(event.getSource()!=null){
                    Log.d(TAG, "Window content change.");
                    handleContentChanged(event);
                }
                break;
            case AccessibilityEvent.TYPE_GESTURE_DETECTION_END:
                Log.d(TAG, "Swipe on Window");
                break;
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                Log.d(TAG, "Click on Window");
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
        //TODO
    }

    // 遍历界面结构（示例）
    private AccessibilityNodeInfo findNode(Rect rect) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        traverseAllNodes(rootNode, allNodes);

        AccessibilityNodeInfo targetNode = null;
        float maxIoU = 0.0f;
        // 打印所有元素边界信息
        for (AccessibilityNodeInfo node : allNodes) {
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            float targetIoU = calculateIoU(bounds, rect);
            if(targetIoU > maxIoU) {
                maxIoU = targetIoU;
                targetNode = node;
            }
        }

        // 必须回收节点
        rootNode.recycle();
        return targetNode;
    }

    private void traverseAllNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> nodes) {
        if (node == null) return;

        nodes.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                traverseAllNodes(child, nodes);
            }
        }
    }
    public static float calculateIoU(Rect rect1, Rect rect2) {
        // 计算交集区域
        int intersectionLeft = Math.max(rect1.left, rect2.left);
        int intersectionTop = Math.max(rect1.top, rect2.top);
        int intersectionRight = Math.min(rect1.right, rect2.right);
        int intersectionBottom = Math.min(rect1.bottom, rect2.bottom);

        // 如果没有交集，返回0
        if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
            return 0.0f;
        }

        // 交集面积
        int intersectionArea = (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop);

        // 两个矩形的面积
        int rect1Area = (rect1.right - rect1.left) * (rect1.bottom - rect1.top);
        int rect2Area = (rect2.right - rect2.left) * (rect2.bottom - rect2.top);

        // 并集面积
        int unionArea = rect1Area + rect2Area - intersectionArea;

        // 计算IoU
        return (float) intersectionArea / unionArea;
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
