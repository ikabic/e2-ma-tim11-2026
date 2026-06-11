package com.slagalica.app.util;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.slagalica.app.R;
import com.slagalica.app.ui.HomeActivity;

public class InviteManager {

    private static final String CHANNEL_ID = "match_invites";

    private String shownInviteId;
    private static InviteManager instance;
    public static InviteManager get() {
        if (instance == null) instance = new InviteManager();
        return instance;
    }

    public String getShownInviteId() {
        return shownInviteId;
    }

    public void setShownInviteId(String shownInviteId) {
        this.shownInviteId = shownInviteId;
    }

    private static final FirebaseDatabase rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/");

    private final MutableLiveData<String[]> incomingInvite = new MutableLiveData<>();
    public LiveData<String[]> getIncomingInvite() { return incomingInvite; }

    private DatabaseReference ref;
    private ChildEventListener listener;

    private Context appContext;
    private int foregroundCount = 0;

    public void startListening(Context context) {
        if (listener != null) return;

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) return;

        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
            createChannel();
            registerForegroundTracking();
        }

        ref = rtdb.getReference("invites");
        listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String prev) {
                FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                if (current == null) return;

                String toUid   = snapshot.child("toUid").getValue(String.class);
                String fromUid = snapshot.child("fromUid").getValue(String.class);
                String status  = snapshot.child("status").getValue(String.class);
                Long createdAt = snapshot.child("createdAt").getValue(Long.class);
                long fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000;

                if ("pending".equals(status) && current.getUid().equals(toUid) && createdAt != null && createdAt > fiveMinutesAgo) {
                    incomingInvite.postValue(new String[]{snapshot.getKey(), fromUid});
                    if (foregroundCount <= 0) {
                        String fromUsername = snapshot.child("fromUsername").getValue(String.class);
                        showInviteNotification(snapshot.getKey(), fromUsername);
                    }
                }
            }
            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        ref.addChildEventListener(listener);
    }

    public void stopListening() {
        if (ref != null && listener != null) ref.removeEventListener(listener);
        listener = null;
        ref = null;
    }

    public void clearIncomingInvite() {
        shownInviteId = null;
        incomingInvite.postValue(null);
    }

    private void registerForegroundTracking() {
        if (!(appContext instanceof Application)) return;
        ((Application) appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity a, Bundle b) {}
            @Override public void onActivityStarted(@NonNull Activity a) { foregroundCount++; }
            @Override public void onActivityResumed(@NonNull Activity a) {}
            @Override public void onActivityPaused(@NonNull Activity a) {}
            @Override public void onActivityStopped(@NonNull Activity a) { if (foregroundCount > 0) foregroundCount--; }
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
            @Override public void onActivityDestroyed(@NonNull Activity a) {}
        });
    }

    private void createChannel() {
        if (appContext == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = appContext.getSystemService(NotificationManager.class);
            if (nm == null) return;
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Match invites", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("Friend match invitations");
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void showInviteNotification(String inviteId, String fromUsername) {
        if (appContext == null) return;

        Intent intent = new Intent(appContext, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(appContext, 1, intent, flags);

        String text = (fromUsername != null ? fromUsername : "A friend") + " invited you to a match";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Match invite")
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(inviteId != null ? inviteId.hashCode() : 5000, builder.build());
    }
}
