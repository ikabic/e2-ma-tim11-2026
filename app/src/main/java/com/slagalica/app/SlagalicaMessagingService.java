package com.slagalica.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.slagalica.app.ui.notifications.NotificationsActivity;

public class SlagalicaMessagingService extends FirebaseMessagingService {

    public static final String ANDROID_CHANNEL_MATCH   = "slagalica_match";
    public static final String ANDROID_CHANNEL_RANKING = "slagalica_ranking";
    public static final String ANDROID_CHANNEL_REWARD  = "slagalica_reward";
    public static final String ANDROID_CHANNEL_OTHER   = "slagalica_other";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title   = null;
        String body    = null;
        String channel = ANDROID_CHANNEL_OTHER;

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body  = remoteMessage.getNotification().getBody();
        }

        if (remoteMessage.getData() != null && !remoteMessage.getData().isEmpty()) {
            if (remoteMessage.getData().containsKey("title"))
                title = remoteMessage.getData().get("title");
            if (remoteMessage.getData().containsKey("body"))
                body = remoteMessage.getData().get("body");

            String dataChannel = remoteMessage.getData().get("channel");
            if (dataChannel != null) {
                switch (dataChannel) {
                    case "match":   channel = ANDROID_CHANNEL_MATCH;   break;
                    case "ranking": channel = ANDROID_CHANNEL_RANKING; break;
                    case "reward":  channel = ANDROID_CHANNEL_REWARD;  break;
                    default: channel = ANDROID_CHANNEL_OTHER;   break;
                }
            }
        }

        if (title != null && body != null) {
            showNotification(title, body, channel);
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        FCMTokenManager.saveToken(token);
    }

    private void showNotification(String title, String body, String androidChannelId) {
        ensureChannelsExist();

        Intent intent = new Intent(this, NotificationsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        int smallIcon = R.drawable.ic_notifications;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, androidChannelId)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    public static void ensureChannelsExist(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        manager.createNotificationChannel(new NotificationChannel(
                ANDROID_CHANNEL_MATCH, "Matches",
                NotificationManager.IMPORTANCE_HIGH));

        manager.createNotificationChannel(new NotificationChannel(
                ANDROID_CHANNEL_RANKING, "Ranking lists",
                NotificationManager.IMPORTANCE_DEFAULT));

        manager.createNotificationChannel(new NotificationChannel(
                ANDROID_CHANNEL_REWARD, "Rewards",
                NotificationManager.IMPORTANCE_HIGH));

        manager.createNotificationChannel(new NotificationChannel(
                ANDROID_CHANNEL_OTHER, "Other",
                NotificationManager.IMPORTANCE_DEFAULT));
    }

    private void ensureChannelsExist() {
        ensureChannelsExist(this);
    }
}