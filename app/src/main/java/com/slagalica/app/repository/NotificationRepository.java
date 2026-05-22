package com.slagalica.app.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.slagalica.app.model.NotificationItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NotificationRepository {

    private static final String NOTIFS_COL = "notifications";

    private final FirebaseFirestore db;
    private final FirebaseAuth  auth;

    public NotificationRepository() {
        db   = FirebaseFirestore.getInstance();
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

    public void createRankingRewardNotif(String targetUserId, int rank, int tokens, String cycleType) {
        String title = cycleType.equals("weekly") ? "Weekly ranking reward 🏆" : "Monthly ranking reward 🏆";
        String body  = "You finished " + ordinal(rank) + " on the " + cycleType + " leaderboard! +" + tokens + " tokens added.";

        pushNotification(targetUserId, NotificationItem.CHANNEL_REWARD, title, body);
    }

    public void createLeagueChangeNotif(String targetUserId, String newLeague) {
        String title = "You moved up to " + newLeague + "! ⬆️";
        String body  = "Congratulations — you've entered the " + newLeague + " League.";
        pushNotification(targetUserId, NotificationItem.CHANNEL_OTHER, title, body);
    }

    public void pushNotification(String targetUserId, String channel, String title, String body) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", targetUserId);
        data.put("channel", channel);
        data.put("title", title);
        data.put("body", body);
        data.put("timestampMs", System.currentTimeMillis());
        data.put("read", false);

        db.collection(NOTIFS_COL).document(UUID.randomUUID().toString()).set(data);
    }

    public void createMatchInviteNotif(String targetUserId, String fromUsername,
                                       String matchId, RepositoryCallback<String> cb) {
        String notifId = UUID.randomUUID().toString();

        Map<String, Object> data = new HashMap<>();
        data.put("userId",        targetUserId);
        data.put("channel",       NotificationItem.CHANNEL_MATCH);
        data.put("title",         "Game invite from " + fromUsername + " 🎮");
        data.put("body",          fromUsername + " wants to play a match with you!");
        data.put("timestampMs",   System.currentTimeMillis());
        data.put("read",          false);
        data.put("actionType",    "match_invite");
        data.put("actionPayload", matchId);
        data.put("actionStatus",  "pending");

        db.collection(NOTIFS_COL).document(notifId).set(data)
                .addOnSuccessListener(v -> cb.onSuccess(notifId))
                .addOnFailureListener(cb::onFailure);
    }

    public void respondToMatchInvite(String notifId, String status, RepositoryCallback<Void> cb) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("actionStatus", status);
        updates.put("read",true);

        db.collection(NOTIFS_COL).document(notifId).update(updates)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(cb::onFailure);
    }

    public void expireMatchInvite(String notifId, RepositoryCallback<Void> cb) {
        db.collection(NOTIFS_COL).document(notifId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String status = doc.getString("actionStatus");
                        if ("pending".equals(status)) {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("actionStatus", "expired");
                            updates.put("read",true);
                            db.collection(NOTIFS_COL).document(notifId)
                                    .update(updates)
                                    .addOnSuccessListener(v -> cb.onSuccess(null))
                                    .addOnFailureListener(cb::onFailure);
                        } else {
                            cb.onSuccess(null);
                        }
                    }
                })
                .addOnFailureListener(cb::onFailure);
    }

    private String ordinal(int n) {
        if (n == 1) return "1st";
        if (n == 2) return "2nd";
        if (n == 3) return "3rd";
        return n + "th";
    }
}