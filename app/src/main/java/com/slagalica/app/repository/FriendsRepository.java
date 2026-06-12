package com.slagalica.app.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.slagalica.app.model.Friend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendsRepository {

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private static final String USERS_COLLECTION = "users";
    private static final String PROFILES_COLLECTION = "profiles";

    private static final FirebaseDatabase rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/");

    public FriendsRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void loadFriends(RepositoryCallback<List<Friend>> callback) {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) { callback.onFailure(new Exception("Not logged in")); return; }

        db.collection(USERS_COLLECTION).document(me.getUid()).get()
                .addOnSuccessListener(userDoc -> {
                    List<String> friendUids = (List<String>) userDoc.get("friends");
                    if (friendUids == null || friendUids.isEmpty()) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }
                    fetchFriends(friendUids, callback);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private void fetchFriends(List<String> uids, RepositoryCallback<List<Friend>> callback) {
        List<Friend> result = new ArrayList<>();
        String cycleId = RankingRepository.currentCycleId("monthly");

        db.collection("rankingCycles").document(cycleId).collection("entries")
                .orderBy("cycleStars", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(rankingSnap -> {
                    Map<String, Integer> rankMap = new HashMap<>();
                    int currentRank = 1;
                    for (DocumentSnapshot doc : rankingSnap) rankMap.put(doc.getId(), currentRank++);

                    int[] pending = {uids.size()};
                    for (String uid : uids) {
                        db.collection(USERS_COLLECTION).document(uid).get()
                                .addOnSuccessListener(userDoc -> {
                                    db.collection(PROFILES_COLLECTION).document(uid).get()
                                            .addOnSuccessListener(profileDoc -> {
                                                int friendRank = rankMap.getOrDefault(uid, 0);

                                                fetchStatusAndBuildFriend(uid, userDoc, profileDoc, friendRank, friend -> {
                                                    synchronized (result) { result.add(friend); }
                                                    if (--pending[0] == 0) sortAndDeliver(result, callback);
                                                });
                                            })
                                            .addOnFailureListener(e -> {
                                                if (--pending[0] == 0) sortAndDeliver(result, callback);
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    if (--pending[0] == 0) sortAndDeliver(result, callback);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    int[] pending = {uids.size()};
                    for (String uid : uids) {
                        db.collection(USERS_COLLECTION).document(uid).get()
                                .addOnSuccessListener(userDoc -> {
                                    db.collection(PROFILES_COLLECTION).document(uid).get()
                                            .addOnSuccessListener(profileDoc -> {
                                                fetchStatusAndBuildFriend(uid, userDoc, profileDoc, 0, friend -> {
                                                    synchronized (result) { result.add(friend); }
                                                    if (--pending[0] == 0) sortAndDeliver(result, callback);
                                                });
                                            })
                                            .addOnFailureListener(exc -> { if (--pending[0] == 0) sortAndDeliver(result, callback); });
                                })
                                .addOnFailureListener(exc -> { if (--pending[0] == 0) sortAndDeliver(result, callback); });
                    }
                });
    }

    private void sortAndDeliver(List<Friend> result, RepositoryCallback<List<Friend>> callback) {
        result.sort((a, b) -> a.getStatusPriority() - b.getStatusPriority());
        callback.onSuccess(result);
    }

    private Friend buildFriend(String uid, DocumentSnapshot userDoc, DocumentSnapshot profileDoc) {
        String username  = userDoc.getString("username");
        String avatarUrl = profileDoc.getString("avatarUrl");
        Long starsLong = profileDoc.getLong("stars");
        int stars = starsLong != null ? starsLong.intValue() : 0;
        Long rankLong = profileDoc.getLong("monthlyRank");
        int rank = rankLong != null ? rankLong.intValue() : 0;
        Long cycleLong = profileDoc.getLong("prevCycleRegionRank");
        int cycle = cycleLong != null ? cycleLong.intValue() : 0;
        return new Friend(uid, username, avatarUrl, stars, true, false, rank, cycle);
    }

    public interface FriendCallback {
        void onFriend(Friend friend);
    }

    private void fetchStatusAndBuildFriend(String uid, DocumentSnapshot userDoc, DocumentSnapshot profileDoc, int calculatedRank, FriendCallback callback) {
        rtdb.getReference("presence")
                .child(uid)
                .get()
                .addOnSuccessListener(snap -> {
                    String status = snap.child("status").getValue(String.class);
                    if (status == null) status = "offline";
                    boolean online = "online".equals(status) || "in_game".equals(status);
                    boolean inGame = "in_game".equals(status);
                    Friend friend = buildFriendFromData(uid, userDoc, profileDoc, online, inGame, calculatedRank);
                    callback.onFriend(friend);
                });
    }

    private Friend buildFriendFromData(String uid, DocumentSnapshot userDoc, DocumentSnapshot profileDoc, boolean online, boolean inGame, int rank) {
        String username = userDoc.getString("username");
        String avatarUrl = profileDoc.getString("avatarUrl");

        Long starsLong = profileDoc.getLong("stars");
        int stars = starsLong != null ? starsLong.intValue() : 0;

        Long cycleLong = profileDoc.getLong("prevCycleRegionRank");
        int cycle = cycleLong != null ? cycleLong.intValue() : 0;

        return new Friend(uid, username, avatarUrl, stars, online, inGame, rank, cycle);
    }

    public void searchByUsername(String query, List<String> existingFriendUids, RepositoryCallback<List<Friend>> callback) {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) { callback.onFailure(new Exception("Not logged in")); return; }

        db.collection(USERS_COLLECTION)
                .whereGreaterThanOrEqualTo("username", query)
                .whereLessThanOrEqualTo("username", query + "\uf8ff")
                .limit(10)
                .get()
                .addOnSuccessListener(snap -> {
                    List<String> uids = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String uid = doc.getId();
                        if (!uid.equals(me.getUid())) uids.add(uid);
                    }
                    if (uids.isEmpty()) { callback.onSuccess(new ArrayList<>()); return; }

                    List<Friend> items = new ArrayList<>();
                    int[] pending = {uids.size()};

                    for (String uid : uids) {
                        boolean alreadyFriend = existingFriendUids.contains(uid);
                        db.collection(PROFILES_COLLECTION).document(uid).get()
                                .addOnSuccessListener(profileDoc -> {
                                    DocumentSnapshot userDoc = snap.getDocuments().stream()
                                            .filter(d -> d.getId().equals(uid)).findFirst().orElse(null);
                                    if (userDoc != null) {
                                        Friend item = buildFriend(uid, userDoc, profileDoc);
                                        synchronized (items) { items.add(item); }
                                    }
                                    if (--pending[0] == 0) callback.onSuccess(items);
                                })
                                .addOnFailureListener(e -> {
                                    if (--pending[0] == 0) callback.onSuccess(items);
                                });
                    }
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void addFriend(String targetUid, RepositoryCallback<Void> callback) {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) { callback.onFailure(new Exception("Not logged in")); return; }

        String myUid = me.getUid();

        db.collection(USERS_COLLECTION).document(myUid)
                .update("friends", FieldValue.arrayUnion(targetUid))
                .addOnSuccessListener(v ->
                        db.collection(USERS_COLLECTION).document(targetUid)
                                .update("friends", FieldValue.arrayUnion(myUid))
                                .addOnSuccessListener(v2 -> callback.onSuccess(null))
                                .addOnFailureListener(callback::onFailure))
                .addOnFailureListener(callback::onFailure);
    }

    public void sendInvite(String toUid, String toUsername, String fromUsername, RepositoryCallback<String> callback) {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) { callback.onFailure(new Exception("Not logged in")); return; }

        DatabaseReference ref = rtdb.getReference("invites").push();
        String inviteId = ref.getKey();

        ref.onDisconnect().removeValue();

        Map<String, Object> values = new HashMap<>();
        values.put("fromUid", me.getUid());
        values.put("toUid", toUid);
        values.put("status", "pending");
        values.put("createdAt", System.currentTimeMillis());
        values.put("fromUsername", fromUsername);
        values.put("toUsername", toUsername);

        ref.setValue(values)
                .addOnSuccessListener(unused -> callback.onSuccess(inviteId))
                .addOnFailureListener(callback::onFailure);
    }

    public void updateInviteStatus(String inviteId, String status) {
        DatabaseReference ref = rtdb.getReference("invites").child(inviteId);;

        Map<String, Object> values = new HashMap<>();
        values.put("status", status);

        ref.updateChildren(values);
    }

    public ListenerRegistration listenForIncomingInvites(IncomingInviteListener listener) {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) return () -> {};

        DatabaseReference ref = rtdb.getReference("invites");

        com.google.firebase.database.ValueEventListener valueListener =
                new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                        for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                            String toUid = child.child("toUid").getValue(String.class);
                            String fromUid = child.child("fromUid").getValue(String.class);
                            String status = child.child("status").getValue(String.class);

                            if ("pending".equals(status) && me.getUid().equals(toUid)) {
                                listener.onInviteReceived(child.getKey(), fromUid);
                            }
                        }
                    }
                    @Override
                    public void onCancelled(com.google.firebase.database.DatabaseError error) {}
                };

        ref.addValueEventListener(valueListener);

        return () -> ref.removeEventListener(valueListener);
    }

    public com.google.firebase.database.ValueEventListener listenToInvite(String inviteId, InviteStatusListener listener) {
        DatabaseReference ref = rtdb.getReference("invites").child(inviteId);

        com.google.firebase.database.ValueEventListener valueListener =
                new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                        String status = snapshot.child("status").getValue(String.class);
                        if (status != null) {
                            listener.onStatusChanged(status);
                        }
                    }
                    @Override
                    public void onCancelled(com.google.firebase.database.DatabaseError error) {}
                };

        ref.addValueEventListener(valueListener);
        return valueListener;
    }

    public interface IncomingInviteListener {
        void onInviteReceived(String inviteId, String fromUid);
    }

    public interface InviteStatusListener {
        void onStatusChanged(String status); // "accepted" | "declined" | "cancelled" | "expired"
    }
}
