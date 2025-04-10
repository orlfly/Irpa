package com.kingsware.irpa;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.zeromq.ZMQ;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class ZeromqServer implements Runnable {
    private static final String TAG = "ZeroMQServer";
    private final Handler msgHandler;
    final ObjectMapper mapper = new ObjectMapper();
    ZMQ.Socket socket;
    ZMQ.Context context;

    @SuppressLint("HandlerLeak")
    private final Handler resHandler = new Handler() {

        public void handleMessage(@NonNull Message message) {
            Log.i(TAG, "Received message: " + message.getData());
            try {
                Map<String,String> msg = (Map<String,String>)message.getData().getSerializable("message");
                Log.i(TAG, "message: " + msg);
                if ( msg != null) {
                    MqMessage<String> mqMsg= new MqMessage<String>(message.getData().getString("uuid"),MqMessage.OPERATION, msg);
                    String response = mapper.writeValueAsString(mqMsg);
                    Log.i(TAG, "Send response: " + response);
                    socket.send(response.getBytes(ZMQ.CHARSET), 0);
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    };
    public static Message bundledMessage(Handler handler, MqMessage<String> msg) {
        Message m = handler.obtainMessage();
        Bundle data = new Bundle();
        data.putString("uuid",msg.getUuid());
        data.putSerializable("message", (HashMap<String,String>)msg.getMessage());
        m.setData(data);
        m.setTarget(handler);
        return m;
    };
    public ZeromqServer(String agentId, String addr, Handler msgHandler) {
        this.msgHandler = msgHandler;
        context = ZMQ.context(1);
        socket = context.socket(ZMQ.DEALER);
        socket.setIdentity(agentId.getBytes(ZMQ.CHARSET));
        Log.i(TAG, "Mq connect:"+addr);
        socket.connect(addr);

        TimerTask heartbeat = new TimerTask() {
            final ObjectMapper mapper = new ObjectMapper();
            @Override
            public void run() {
                try {
                    Log.i(TAG, "Send heartbeat.......");
                    Map<String, String> message = new HashMap<>();
                    message.put("agent",agentId);
                    message.put("status","on");
                    String heartbeatMessage = mapper.writeValueAsString(new MqMessage<String>(MqMessage.HEARTBEAT, message));
                    Log.i(TAG, "Send"+heartbeatMessage);
                    socket.send(heartbeatMessage.getBytes(ZMQ.CHARSET), 0);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Timer timer = new Timer("Heartbeat");
        timer.schedule(heartbeat, 30, 3000);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        socket.close();
        context.term();
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            Log.i(TAG, "recv..............");
            byte[] message = socket.recv(0);
            if (message != null) {
                String receivedMessage = new String(message, ZMQ.CHARSET);
                Log.i(TAG, "recv:"+receivedMessage);
                try {
                    MqMessage<String> msg = mapper.readValue(receivedMessage, new TypeReference<MqMessage<String>>() {});
                    msgHandler.handleMessage(bundledMessage(resHandler, msg));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
