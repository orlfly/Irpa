package com.kingsware.irpa.automation;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class AutoAccessibilityService extends AccessibilityService {
    private static final String TAG = "AutoAccessibilityService";
    private AppController controller;
    private static AutoAccessibilityService instance;
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
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Accessibility Service Created");
        instance = this;
    }
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 处理无障碍事件
        Log.d(TAG, "Event Type: " + AccessibilityEvent.eventTypeToString(event.getEventType()));
        Log.d(TAG, "Event Text: " + event.getText());
    }

    @Override
    public void onInterrupt() {
        // 当服务被中断时调用
        Log.d(TAG, "Accessibility Service Interrupted");
    }

    public static class UiElement {
        private String className;
        private String text;
        private Rect bounds;

        public UiElement(String className, String text, Rect bounds) {
            this.className = className;
            this.text = text;
            this.bounds = bounds;
        }

        // Getters
        public String getClassName() { return className; }
        public String getText() { return text; }
        public Rect getBounds() { return bounds; }
    }
}
