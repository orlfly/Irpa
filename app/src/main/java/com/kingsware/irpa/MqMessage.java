package com.kingsware.irpa;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@JsonPropertyOrder({
        "uuid",
        "type",
        "message"
})
public class MqMessage<T> {
    public static final String HEARTBEAT="hearbeat";
    public static final String OPERATION="operation";
    @JsonProperty("uuid")
    private String uuid;
    @JsonProperty("type")
    private String type;
    @JsonProperty("message")
    private Map<String,T> message = new HashMap<String,T>();
    public MqMessage(){
        this.uuid=UUID.randomUUID().toString();
        this.type=OPERATION;
        this.message=new HashMap<String,T>();
    }
    public MqMessage(String type,Map<String,T> message){
        this.uuid=UUID.randomUUID().toString();
        this.type=type;
        this.message=message;
    }
    public MqMessage(String uuid,String type,Map<String,T> message){
        this.uuid=uuid;
        this.type=type;
        this.message=message;
    }
    @JsonProperty("type")
    public String getType() {
        return type;
    }
    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("message")
    public Map<String,T> getMessage() {
        return message;
    }

    @JsonProperty("message")
    public void setMessage(Map<String,T> message) {
        this.message = message;
    }

    @JsonProperty("uuid")
    public String getUuid() {
        return uuid;
    }
}
