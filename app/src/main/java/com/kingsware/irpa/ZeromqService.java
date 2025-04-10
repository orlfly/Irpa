package com.kingsware.irpa;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZeromqService extends Service {
    private static final String TAG = "ZeromqService";
    private static final String PREFS_NAME = "ZeromqPrefs";
    private static final String KEY_SERVER_ADDRESS = "server_address";

    String agentId = "agent_"+UUID.randomUUID().toString();

    @SuppressLint("HandlerLeak")
    private final Handler msgHandler = new Handler() {
        public void handleMessage(@NonNull Message message) {
            Log.i(TAG, "Received message: "+message.getData());
            String uuid = message.getData().getString("uuid");
            Message m = this.obtainMessage();
            Bundle data = new Bundle();
            HashMap<String,String> resp = new HashMap<String,String>();
            resp.put("exec","fin");
            data.putString("uuid",uuid);
            data.putSerializable("message", resp);
            m.setData(data);
            message.getTarget().handleMessage(m);
        }
    };

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        ZeromqService getService() {
            return ZeromqService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
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