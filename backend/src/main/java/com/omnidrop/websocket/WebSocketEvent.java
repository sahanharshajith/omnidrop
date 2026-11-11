package com.omnidrop.websocket;

import java.io.Serializable;

public class WebSocketEvent implements Serializable {

    private String type;
    private long timestamp;
    private Object payload;

    public WebSocketEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public WebSocketEvent(String type, Object payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}
