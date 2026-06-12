package com.slagalica.app.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.slagalica.app.model.DailyMissions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DailyMissionRepository {

    private static final String PROFILES_COLLECTION = "profiles";
    private static final String MISSIONS_COLLECTION = "dailyMissions";
    public static final String KEY_WIN_MATCH = "winMatch";
    public static final String KEY_SEND_CHAT  = "sendChat";
    public static final String KEY_PLAY_FRIENDLY = "playFriendly";
    public static final String KEY_WIN_TOURNAMENT = "winTournament";
    public static final String KEY_CLAIMED_COUNT = "claimedCount";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public DailyMissionRepository() {
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public String getUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    public static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private com.google.firebase.firestore.DocumentReference todayDoc(String uid) {
        return db.collection(PROFILES_COLLECTION)
                .document(uid)
                .collection(MISSIONS_COLLECTION)
                .document(todayKey());
    }

    public void loadTodayMissions(RepositoryCallback<DailyMissions> callback) {
        String uid = getUid();
        if (uid == null) { callback.onFailure(new Exception("Not logged in")); return; }

        todayDoc(uid).get().addOnSuccessListener(snap -> {
            if (snap.exists()) {
                DailyMissions m = snap.toObject(DailyMissions.class);
                callback.onSuccess(m != null ? m : new DailyMissions());
            } else {
                DailyMissions fresh = new DailyMissions();
                todayDoc(uid).set(fresh).addOnSuccessListener(v -> callback.onSuccess(fresh)).addOnFailureListener(callback::onFailure);
            }
        }).addOnFailureListener(callback::onFailure);
    }

    public ListenerRegistration listenTodayMissions(MissionsListener listener) {
        String uid = getUid();
        if (uid == null) return () -> {};

        return todayDoc(uid).addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            if (snap.exists()) {
                DailyMissions m = snap.toObject(DailyMissions.class);
                listener.onChanged(m != null ? m : new DailyMissions());
            }
        });
    }

    public void completeMission(String missionKey, RepositoryCallback<Void> callback) {
        String uid = getUid();
        if (uid == null) {
            if (callback != null) callback.onFailure(new Exception("Not logged in"));
            return;
        }

        com.google.firebase.firestore.DocumentReference ref = todayDoc(uid);

        ref.get().addOnSuccessListener(snap -> {
            if (snap.exists()) {
                Map<String, Object> update = new HashMap<>();
                update.put(missionKey, true);
                ref.update(update)
                        .addOnSuccessListener(v -> { if (callback != null) callback.onSuccess(null); })
                        .addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });
            } else {
                Map<String, Object> fresh = new DailyMissions().toMap();
                fresh.put(missionKey, true);
                ref.set(fresh).addOnSuccessListener(v -> { if (callback != null) callback.onSuccess(null); })
                        .addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });
            }
        }).addOnFailureListener(e -> {
            if (callback != null) callback.onFailure(e);
        });
    }

    public void completeMission(String missionKey) {
        completeMission(missionKey, null);
    }

    public void claimRewards(DailyMissions missions, RepositoryCallback<ClaimResult> callback) {
        String uid = getUid();
        if (uid == null) { callback.onFailure(new Exception("Not logged in")); return; }

        int newlyCompleted = missions.unclaimedCount();
        if (newlyCompleted <= 0) { callback.onFailure(new Exception("Nothing new to claim")); return; }

        int starsEarned = newlyCompleted * 3;
        int completedNow = missions.completedCount();
        int prevClaimed = missions.getClaimedCount();

        boolean bonusUnlocked = completedNow == 4 && prevClaimed < 4;
        int bonusStars = bonusUnlocked ? 3 : 0;
        int tokenBonus = bonusUnlocked ? 2 : 0;
        int totalStars = starsEarned + bonusStars;

        Map<String, Object> missionUpdate = new HashMap<>();
        missionUpdate.put(KEY_CLAIMED_COUNT, completedNow);

        todayDoc(uid).update(missionUpdate).addOnSuccessListener(v -> {
            Map<String, Object> profileUpdate = new HashMap<>();
            profileUpdate.put("stars", FieldValue.increment(totalStars));
            if (tokenBonus > 0) profileUpdate.put("tokens", FieldValue.increment(tokenBonus));

            db.collection(PROFILES_COLLECTION).document(uid)
                    .update(profileUpdate)
                    .addOnSuccessListener(v2 -> {
                        RankingRepository rankRepo = new RankingRepository();
                        rankRepo.addStarsToCycle("weekly", totalStars, new RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void r) {}
                            @Override public void onFailure(Exception e) {}
                        });
                        rankRepo.addStarsToCycle("monthly", totalStars, new RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void r) {}
                            @Override public void onFailure(Exception e) {}
                        });
                        callback.onSuccess(new ClaimResult(totalStars, tokenBonus, bonusUnlocked));
                    })
                    .addOnFailureListener(callback::onFailure);
        }).addOnFailureListener(callback::onFailure);
    }

    public void hasUnclaimedRewards(RepositoryCallback<Boolean> callback) {
        String uid = getUid();
        if (uid == null) { callback.onSuccess(false); return; }

        todayDoc(uid).get().addOnSuccessListener(snap -> {
            if (!snap.exists()) { callback.onSuccess(false); return; }
            DailyMissions m = snap.toObject(DailyMissions.class);
            if (m == null) { callback.onSuccess(false); return; }
            callback.onSuccess(m.hasUnclaimedRewards());
        }).addOnFailureListener(e -> callback.onSuccess(false));
    }

    public interface MissionsListener {
        void onChanged(DailyMissions missions);
    }

    public static class ClaimResult {
        public final int starsEarned;
        public final int tokensEarned;
        public final boolean bonusUnlocked;

        public ClaimResult(int starsEarned, int tokensEarned, boolean bonusUnlocked) {
            this.starsEarned   = starsEarned;
            this.tokensEarned  = tokensEarned;
            this.bonusUnlocked = bonusUnlocked;
        }
    }
}
