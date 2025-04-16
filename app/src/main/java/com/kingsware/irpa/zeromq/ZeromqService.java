package com.kingsware.irpa.zeromq;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.kingsware.irpa.FloatingWindowService;
import com.kingsware.irpa.R;
import com.kingsware.irpa.automation.AutoAccessibilityService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ZeromqService extends Service {
    private static final String TAG = "ZeromqService";
    private static final String PREFS_NAME = "ZeromqPrefs";
    private static final String KEY_SERVER_ADDRESS = "server_address";

    String agentId = "agent_"+UUID.randomUUID().toString();
    
    ArrayList<Map<String,String>> appList=new ArrayList<>();

    private MessageDisplayer messageDisplayer = null;
    private AutoAccessibilityService autoAccessibilityService=null;
    public void setAutoAccessibilityService(AutoAccessibilityService autoAccessibilityService) {
        this.autoAccessibilityService = autoAccessibilityService;
    }

    public void setMessageDisplayer(MessageDisplayer messageDisplayer) {
        this.messageDisplayer = messageDisplayer;
    }

    @SuppressLint("HandlerLeak")
    private final Handler msgHandler = new Handler() {
        public void handleMessage(@NonNull Message message) {
            Log.i(TAG, "Received message: "+message.getData());
            String uuid = message.getData().getString("uuid");
            Map<String,String> msg = (Map<String,String>)message.getData().getSerializable("message");
            if(msg!=null && msg.containsKey("operation")) {
                Message m = this.obtainMessage();
                Bundle data = new Bundle();
                data.putString("uuid",uuid);
                switch(Objects.requireNonNull(msg.get("operation"))){
                    case "apps":
                        data.putSerializable("message", appList);
                        break;
                    case "start":
                        String startPackageName = msg.get("packageName");
                        if(autoAccessibilityService!=null) {
                            autoAccessibilityService.launchApp(startPackageName);
                        }
                        data.putSerializable("message", "start fin");
                        break;
                    case "stop":
                        String staopPackageName = msg.get("packageName");
                        if(autoAccessibilityService!=null) {
                            autoAccessibilityService.stopApp(staopPackageName);
                        }
                        data.putSerializable("message", "stop fin");
                        break;
                    case "display":
                        if(messageDisplayer!=null){
                            if(msg.containsKey("text")) {
                                String text = msg.get("text");

                                if (msg.containsKey("duration")) {
                                    int duration = Integer.parseInt(msg.get("duration"));
                                    messageDisplayer.displayMessage(text, duration);
                                } else {
                                    messageDisplayer.displayMessage(text);
                                }
                            }
                        }
                        data.putSerializable("message", "display fin");
                    default:
                        break;
                }
                m.setData(data);
                message.getTarget().handleMessage(m);
            }
        }
    };

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public ZeromqService getService() {
            return ZeromqService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo app : apps) {
            HashMap<String,String> resp = new HashMap<String,String>();
            resp.put("name", getPackageManager().getApplicationLabel(app).toString());
            resp.put("package", app.packageName);
            appList.add(resp);
        }
        Log.i(TAG, "App list:"+appList);
        Log.i(TAG, "server start");
        ZeromqServer server = new ZeromqServer(agentId, getServerAddress(), msgHandler);
        Thread zmqThread = new Thread(server);
        zmqThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service start");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setServerAddress(String address) {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_SERVER_ADDRESS, address);
        editor.apply();
    }

    private String getServerAddress() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getString(KEY_SERVER_ADDRESS,  getString(R.string.server_address));
    }
}