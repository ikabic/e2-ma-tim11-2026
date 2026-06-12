package com.slagalica.app.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.slagalica.app.R;
import com.slagalica.app.model.ChatMessage;
import com.slagalica.app.ui.chat.ChatActivity;

public class ChatNotificationManager {

    private static final String CHANNEL_ID = "chat_messages";
    private static final FirebaseDatabase rtdb = FirebaseDatabase.getInstance(
        "https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/");

    private static ChatNotificationManager instance;
    public static ChatNotificationManager get() {
        if (instance == null) instance = new ChatNotificationManager();
        return instance;
    }

    private static String visibleRegion;
    public static void setVisibleRegion(String region) { visibleRegion = region; }

    private DatabaseReference ref;
    private ChildEventListener listener;
    private String regionKey;
    private String myUid;
    private long startedAt;
    private int notifId = 4000;

    public void start(Context context, String regionKey, String myUid) {
        if (listener != null) return;
        if (regionKey == null || regionKey.isEmpty() || myUid == null) return;

        this.regionKey = regionKey;
        this.myUid = myUid;
        this.startedAt = System.currentTimeMillis();
        createChannel(context);

        final Context appContext = context.getApplicationContext();
        ref = rtdb.getReference("chat").child(regionKey);
        listener = new ChildEventListener() {
            @Override public void onChildAdded(@NonNull DataSnapshot snapshot, String prev) {
                ChatMessage m = snapshot.getValue(ChatMessage.class);
                if (m == null) return;
                if (m.getTimestamp() <= startedAt) return;
                if (myUid.equals(m.getSenderUid())) return;
                if (regionKey.equals(visibleRegion)) return;
                showNotification(appContext, m);
            }
            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        ref.addChildEventListener(listener);
    }

    public void stop() {
        if (ref != null && listener != null) ref.removeEventListener(listener);
        ref = null;
        listener = null;
        regionKey = null;
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm == null) return;
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Chat messages", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("New messages in your region chat");
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void showNotification(Context context, ChatMessage m) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra("regionKey", regionKey);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, flags);

        String title = m.getSenderName() != null ? m.getSenderName() : "New message";
        String text = m.getText() != null ? m.getText() : "";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(notifId++, builder.build());
    }
}
