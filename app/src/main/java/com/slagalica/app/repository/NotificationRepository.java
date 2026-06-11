package com.slagalica.app.repository;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.slagalica.app.R;
import com.slagalica.app.SlagalicaMessagingService;
import com.slagalica.app.model.NotificationItem;
import com.slagalica.app.ui.notifications.NotificationsActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NotificationRepository {

    private static final String NOTIFS_COL = "notifications";
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Context context;

    public NotificationRepository(Context context) {
        this.context = context.getApplicationContext();
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public NotificationRepository() {
        this.context = null;
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    private String uid() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    public void fetchAll(RepositoryCallback<List<NotificationItem>> cb) {
        String uid = uid();
        if (uid == null) { cb.onSuccess(new ArrayList<>()); return; }

        db.collection(NOTIFS_COL).whereEqualTo("userId", uid)
                .orderBy("timestampMs", Query.Direction.DESCENDING).limit(50).get()
                .addOnSuccessListener(snap -> {
                    List<NotificationItem> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        NotificationItem item = doc.toObject(NotificationItem.class);
                        item.setId(doc.getId());
                        list.add(item);
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(cb::onFailure);
    }

    public void markRead(String notifId, RepositoryCallback<Void> cb) {
        db.collection(NOTIFS_COL).document(notifId).update("read", true)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onFailure);
    }

    public void markAllRead(String uid, RepositoryCallback<Void> cb) {
        db.collection(NOTIFS_COL)
                .whereEqualTo("userId", uid)
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) { cb.onSuccess(null); return; }
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : snap) {
                        batch.update(doc.getReference(), "read", true);
                    }
                    batch.commit()
                            .addOnSuccessListener(v -> cb.onSuccess(null))
                            .addOnFailureListener(cb::onFailure);
                })
                .addOnFailureListener(cb::onFailure);
    }

    public void createMatchResultNotif(String targetUserId, String opponentName, int myScore, int opponentScore) {
        String title;
        String body;

        if (myScore > opponentScore) {
            title = "Win!";
            body  = "You won against " + opponentName + " with results " + myScore + " : " + opponentScore + ".";
        } else if (myScore < opponentScore) {
            title = "Defeat";
            body  = "You lost from " + opponentName + " with results " + myScore + " : " + opponentScore + ".";
        } else {
            title = "A draw";
            body  = "A match with " + opponentName + " was finished with a draw with results " + myScore + " : " + opponentScore + ".";
        }

        pushNotification(targetUserId, NotificationItem.CHANNEL_MATCH, title, body);
        sendSystemPush(title, body, SlagalicaMessagingService.ANDROID_CHANNEL_MATCH);
    }

    public void createMatchInviteNotif(String targetUserId, String fromUsername,
                                       String matchId, RepositoryCallback<String> cb) {
        String notifId = UUID.randomUUID().toString();

        Map<String, Object> data = new HashMap<>();
        data.put("userId", targetUserId);
        data.put("channel", NotificationItem.CHANNEL_MATCH);
        data.put("title","Match invite from " + fromUsername);
        data.put("body",fromUsername + " wants to match with you!");
        data.put("timestampMs", System.currentTimeMillis());
        data.put("read", false);
        data.put("actionType","match_invite");
        data.put("actionPayload", matchId);
        data.put("actionStatus","pending");

        db.collection(NOTIFS_COL).document(notifId).set(data)
                .addOnSuccessListener(v -> {
                    sendSystemPush(
                            "Match invite from " + fromUsername,
                            fromUsername + " wants to match with you!",
                            SlagalicaMessagingService.ANDROID_CHANNEL_MATCH);
                    cb.onSuccess(notifId);
                })
                .addOnFailureListener(cb::onFailure);
    }

    public void respondToMatchInvite(String notifId, String status, RepositoryCallback<Void> cb) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("actionStatus", status);
        updates.put("read", true);
        db.collection(NOTIFS_COL).document(notifId).update(updates)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onFailure);
    }

    public void expireMatchInvite(String notifId, RepositoryCallback<Void> cb) {
        db.collection(NOTIFS_COL).document(notifId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String status = doc.getString("actionStatus");
                        if ("pending".equals(status)) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("actionStatus", "expired");
                            updates.put("read", true);
                            db.collection(NOTIFS_COL).document(notifId).update(updates)
                                    .addOnSuccessListener(v -> cb.onSuccess(null))
                                    .addOnFailureListener(cb::onFailure);
                        } else {
                            cb.onSuccess(null);
                        }
                    }
                })
                .addOnFailureListener(cb::onFailure);
    }

    public void createRankingRewardNotif(String targetUserId, int rank, int tokens, String cycleType) {
        String title = cycleType.equals("weekly") ? "Weekly ranking list reward" : "Monthly ranking list reward";
        String body  = "You are " + ordinal(rank) + " place on the " +
                (cycleType.equals("weekly") ? "weekly" : "monthly") +
                " ranking list! +" + tokens + " tokens granted.";

        pushNotification(targetUserId, NotificationItem.CHANNEL_REWARD, title, body);
        sendSystemPush(title, body, SlagalicaMessagingService.ANDROID_CHANNEL_REWARD);
    }

    public void createLeagueChangeNotif(String targetUserId, String newLeague) {
        String title = "Upgrade to " + newLeague + " league";
        String body  = "Congrats — you entered " + newLeague + " league!";
        pushNotification(targetUserId, NotificationItem.CHANNEL_OTHER, title, body);
        sendSystemPush(title, body, SlagalicaMessagingService.ANDROID_CHANNEL_OTHER);
    }

    public void createRankingResultNotif(String targetUserId, int rank, String cycleType) {
        String label = cycleType.equals("weekly") ? "weekly" : "monthly";
        String title = "Results of " + (cycleType.equals("weekly") ? "weekly" : "monthly") + " ranking list";
        String body  = "You ended up on " + ordinal(rank) + " place on the " + label + " ranking list.";
        pushNotification(targetUserId, NotificationItem.CHANNEL_RANKING, title, body);
        sendSystemPush(title, body, SlagalicaMessagingService.ANDROID_CHANNEL_RANKING);
    }

    public void createChatMessageNotif(String targetUserId, String fromUsername, String preview) {
        String title = "New message from " + fromUsername;
        String body  = preview;
        pushNotification(targetUserId, NotificationItem.CHANNEL_CHAT, title, body);
        sendSystemPush(title, body, SlagalicaMessagingService.ANDROID_CHANNEL_OTHER);
    }

    public void pushNotification(String targetUserId, String channel, String title, String body) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", targetUserId);
        data.put("channel", channel);
        data.put("title", title);
        data.put("body", body);
        data.put("timestampMs", System.currentTimeMillis());
        data.put("read",false);
        db.collection(NOTIFS_COL).document(UUID.randomUUID().toString()).set(data);
    }

    private void sendSystemPush(String title, String body, String androidChannelId) {
        if (context == null) return;

        SlagalicaMessagingService.ensureChannelsExist(context);

        Intent intent = new Intent(context, NotificationsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, androidChannelId)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private String ordinal(int n) {
        if (n == 1) return "1.";
        if (n == 2) return "2.";
        if (n == 3) return "3.";
        return n + ".";
    }
}