package com.slagalica.app.model;

public class NotificationItem {

    public static final String CHANNEL_CHAT    = "chat";
    public static final String CHANNEL_RANKING = "ranking";
    public static final String CHANNEL_REWARD  = "reward";
    public static final String CHANNEL_OTHER   = "other";

    private String  id;
    private String  channel;
    private String  title;
    private String  body;
    private long    timestampMs;
    private boolean read;

    public NotificationItem() {}

    public NotificationItem(String id, String channel, String title, String body,
                            long timestampMs, boolean read) {
        this.id = id;
        this.channel = channel;
        this.title = title;
        this.body = body;
        this.timestampMs = timestampMs;
        this.read = read;
    }

    public String  getId()           { return id; }
    public void setId(String id)  { this.id = id; }

    public String  getChannel()                  { return channel; }
    public void setChannel(String channel)    { this.channel = channel; }

    public String  getTitle()                    { return title; }
    public void setTitle(String title)        { this.title = title; }

    public String  getBody()                     { return body; }
    public void setBody(String body)          { this.body = body; }

    public long getTimestampMs()              { return timestampMs; }
    public void setTimestampMs(long ms)       { this.timestampMs = ms; }

    public boolean isRead()                      { return read; }
    public void setRead(boolean read)         { this.read = read; }
}