package com.kingsware.irpa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MainPanelActivity extends AppCompatActivity {
    private static final String TAG = "MainPanelActivity";
    private static final String PREFS_NAME = "ServerPrefs";
    private static final String KEY_SERVER_ADDRESS = String.valueOf(R.string.server_address);
    public static final String EXTRA_SERIAL = "serial";

    private ZeromqService mService;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        setContentView(R.layout.activity_main_panel);
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        TextView operator = findViewById(R.id.device_property_operator);
        operator.setText(tm.getSimOperatorName());

        String serial = intent.getStringExtra(EXTRA_SERIAL);

        if (serial == null) {
            serial = getProperty("ro.serialno", "unknown");
        }
        TextView serialview = findViewById(R.id.device_property_serial);
        serialview.setText(serial);

        TextView version = findViewById(R.id.device_property_version);
        version.setText(Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");

        // 这里可以添加主界面内容
        TextView textView = findViewById(R.id.text_view);
        textView.setText("服务器配置");

        EditText etServerAddress = findViewById(R.id.et_server_address);
        Button btnSaveAddress = findViewById(R.id.btn_save_address);

        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedAddress = preferences.getString(KEY_SERVER_ADDRESS, "");
        etServerAddress.setText(savedAddress);

        btnSaveAddress.setOnClickListener(v -> {
            String serverAddress = etServerAddress.getText().toString();
            if (!serverAddress.isEmpty()) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(KEY_SERVER_ADDRESS, serverAddress);
                editor.apply();
                Toast.makeText(this, "Server address saved", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Please enter a server address", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private String getProperty(String name, String defaultValue) {
        try {
            Class<?> SystemProperties = Class.forName("android.os.SystemProperties");
            try {
                Method get = SystemProperties.getMethod("get", String.class, String.class);
                return (String) get.invoke(SystemProperties, name, defaultValue);
            } catch (NoSuchMethodException e) {
                Method get = SystemProperties.getMethod("get", String.class);
                return (String) get.invoke(SystemProperties, name);
            }
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Class.forName() failed", e);
            return defaultValue;
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "getMethod() failed", e);
            return defaultValue;
        } catch (InvocationTargetException e) {
            Log.e(TAG, "invoke() failed", e);
            return defaultValue;
        } catch (IllegalAccessException e) {
            Log.e(TAG, "invoke() failed", e);
            return defaultValue;
        }
    }
}