package com.kingsware.irpa.zeromq;

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
    private T message;
    public MqMessage(){
        this.uuid=UUID.randomUUID().toString();
        this.type=OPERATION;
    }
    public MqMessage(String type,T message){
        this.uuid=UUID.randomUUID().toString();
        this.type=type;
        this.message=message;
    }
    public MqMessage(String uuid,String type,T message){
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
    public T getMessage() {
        return message;
    }

    @JsonProperty("message")
    public void setMessage(T message) {
        this.message = message;
    }

    @JsonProperty("uuid")
    public String getUuid() {
        return uuid;
    }
}
