package com.slagalica.app.model;

public class ChatMessage {

    private String senderUid;
    private String senderName;
    private String text;
    private long timestamp;

    public ChatMessage() {}

    public ChatMessage(String senderUid, String senderName, String text, long timestamp) {
        this.senderUid = senderUid;
        this.senderName = senderName;
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getSenderUid() { return senderUid; }
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
