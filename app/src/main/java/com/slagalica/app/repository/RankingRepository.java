package com.slagalica.app.repository;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.slagalica.app.model.RankingCycle;
import com.slagalica.app.model.RankingEntry;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class RankingRepository {

    private static final String CYCLES_COL  = "rankingCycles";
    private static final String ENTRIES_COL = "entries";
    private static final String USERS_COL   = "users";
    private static final String PROFILES_COL = "profiles";
    private static final int[] WEEKLY_REWARDS  = {0, 5, 3, 2, 1};
    private static final int[] MONTHLY_REWARDS = {0, 10, 6, 4, 2};

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public RankingRepository() {
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public void addStarsToCycle(String type, int stars, RepositoryCallback<Void> cb) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) { cb.onFailure(new Exception("Not logged in")); return; }

        String cycleId = currentCycleId(type);
        ensureCycleExists(cycleId, type, () -> {
            String entryPath = CYCLES_COL + "/" + cycleId + "/" + ENTRIES_COL + "/" + uid;

            db.collection(USERS_COL).document(uid).get().addOnSuccessListener(userDoc -> {
                String username = userDoc.getString("username");
                db.collection(PROFILES_COL).document(uid).get().addOnSuccessListener(profDoc -> {
                    long totalStars = profDoc.getLong("stars") != null ? profDoc.getLong("stars") : 0;

                    db.document(entryPath).get().addOnSuccessListener(entryDoc -> {
                        if (entryDoc.exists()) {
                            db.document(entryPath)
                                    .update("cycleStars", FieldValue.increment(stars),
                                            "totalStars", totalStars,
                                            "username", username)
                                    .addOnSuccessListener(v -> cb.onSuccess(null))
                                    .addOnFailureListener(cb::onFailure);
                        } else {
                            RankingEntry entry = new RankingEntry(uid, username, stars, (int) totalStars);
                            db.document(entryPath).set(entry)
                                    .addOnSuccessListener(v -> cb.onSuccess(null))
                                    .addOnFailureListener(cb::onFailure);
                        }
                    }).addOnFailureListener(cb::onFailure);
                }).addOnFailureListener(cb::onFailure);
            }).addOnFailureListener(cb::onFailure);
        });
    }

    public void fetchLeaderboard(String type, RepositoryCallback<List<RankingEntry>> cb) {
        String cycleId = currentCycleId(type);
        db.collection(CYCLES_COL).document(cycleId).collection(ENTRIES_COL)
                .orderBy("cycleStars", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snap -> {
                    List<RankingEntry> list = new ArrayList<>();
                    int rank = 1;
                    for (QueryDocumentSnapshot doc : snap) {
                        RankingEntry e = doc.toObject(RankingEntry.class);
                        e.setUserId(doc.getId());
                        e.setRank(rank++);
                        list.add(e);
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(cb::onFailure);
    }

    public void fetchCurrentCycle(String type, RepositoryCallback<RankingCycle> cb) {
        String cycleId = currentCycleId(type);
        ensureCycleExists(cycleId, type, () ->
                db.collection(CYCLES_COL).document(cycleId).get()
                        .addOnSuccessListener(doc -> {
                            RankingCycle cycle = doc.toObject(RankingCycle.class);
                            if (cycle != null) cycle.setId(doc.getId());
                            cb.onSuccess(cycle);
                        })
                        .addOnFailureListener(cb::onFailure));
    }

    public void distributeRewards(String cycleId, String type,
                                  RepositoryCallback<List<RewardResult>> cb) {
        db.collection(CYCLES_COL).document(cycleId)
                .collection(ENTRIES_COL)
                .orderBy("cycleStars", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(snap -> {
                    int[] rewards = type.equals("weekly") ? WEEKLY_REWARDS : MONTHLY_REWARDS;
                    List<RewardResult> results = new ArrayList<>();
                    int rank = 1;
                    for (QueryDocumentSnapshot doc : snap) {
                        String uid = doc.getId();
                        int tokens = rank < rewards.length ? rewards[rank] : 0;
                        if (tokens > 0) {
                            db.collection(PROFILES_COL).document(uid)
                                    .update("tokens", FieldValue.increment(tokens));
                        }
                        String username = doc.getString("username");
                        results.add(new RewardResult(uid, username, rank, tokens, type));
                        rank++;
                    }
                    db.collection(CYCLES_COL).document(cycleId)
                            .update("rewardsDistributed", true);
                    cb.onSuccess(results);
                })
                .addOnFailureListener(cb::onFailure);
    }

    public void secureAwardDistribution(String pastCycleId, RepositoryCallback<Void> cb) {
        final DocumentReference cycleRef = db.collection(CYCLES_COL).document(pastCycleId);

        db.runTransaction(transaction -> {
            DocumentSnapshot cycleSnap = transaction.get(cycleRef);

            if (!cycleSnap.exists()) return null;

            Boolean distributed = cycleSnap.getBoolean("rewardsDistributed");
            Timestamp endDate = cycleSnap.getTimestamp("endDate");

            if (Boolean.TRUE.equals(distributed) || (endDate != null && endDate.compareTo(Timestamp.now()) > 0))
                return null;

            transaction.update(cycleRef, "rewardsDistributed", true);
            return true;
        }).addOnSuccessListener(result -> {
            if (result != null) {
                String type = pastCycleId.contains("weekly") ? "weekly" : "monthly";
                distributeRewards(pastCycleId, type, new RepositoryCallback<>() {
                    @Override public void onSuccess(List<RewardResult> r) { cb.onSuccess(null); }
                    @Override public void onFailure(Exception e) { cb.onFailure(e); }
                });
            } else
                cb.onSuccess(null);
        }).addOnFailureListener(cb::onFailure);
    }

    public static String currentCycleId(String type) {
        Calendar cal = Calendar.getInstance();
        if (type.equals("weekly")) {
            int year = cal.get(Calendar.YEAR);
            int week = cal.get(Calendar.WEEK_OF_YEAR);
            return String.format("weekly_%04d_W%02d", year, week);
        } else {
            int year  = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            return String.format("monthly_%04d_%02d", year, month);
        }
    }

    private void ensureCycleExists(String cycleId, String type, Runnable onDone) {
        db.collection(CYCLES_COL).document(cycleId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        RankingCycle cycle = new RankingCycle();
                        cycle.setId(cycleId);
                        cycle.setType(type);
                        cycle.setRewardsDistributed(false);

                        Calendar cal = Calendar.getInstance();
                        if (type.equals("weekly")) {
                            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                            cycle.setStartDate(new Timestamp(cal.getTime()));
                            cal.add(Calendar.DAY_OF_YEAR, 6);
                            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59);
                            cycle.setEndDate(new Timestamp(cal.getTime()));
                        } else {
                            cal.set(Calendar.DAY_OF_MONTH, 1);
                            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                            cycle.setStartDate(new Timestamp(cal.getTime()));
                            cal.set(Calendar.DAY_OF_MONTH,
                                    cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59);
                            cycle.setEndDate(new Timestamp(cal.getTime()));
                        }
                        db.collection(CYCLES_COL).document(cycleId).set(cycle)
                                .addOnSuccessListener(v -> onDone.run())
                                .addOnFailureListener(e -> onDone.run());
                    } else {
                        onDone.run();
                    }
                })
                .addOnFailureListener(e -> onDone.run());
    }

    public void fetchUserMonthlyRank(String userId, RepositoryCallback<Integer> cb) {
        String cycleId = currentCycleId("monthly");
        db.collection(CYCLES_COL).document(cycleId)
                .collection(ENTRIES_COL)
                .orderBy("cycleStars", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snap -> {
                    int rank = 1;
                    for (QueryDocumentSnapshot doc : snap) {
                        if (doc.getId().equals(userId)) {
                            cb.onSuccess(rank);
                            return;
                        }
                        rank++;
                    }
                    cb.onSuccess(0);
                })
                .addOnFailureListener(cb::onFailure);
    }

    public void fetchRegionLeaderboard(RepositoryCallback<Map<String, Integer>> cb) {
        String cycleId = currentCycleId("monthly");
        db.collection(CYCLES_COL).document(cycleId)
                .collection(ENTRIES_COL)
                .get()
                .addOnSuccessListener(entriesSnap -> {
                    if (entriesSnap.isEmpty()) { cb.onSuccess(new HashMap<>()); return; }

                    List<String> userIds = new ArrayList<>();
                    Map<String, Long> starsByUser = new HashMap<>();
                    for (QueryDocumentSnapshot doc : entriesSnap) {
                        userIds.add(doc.getId());
                        Long s = doc.getLong("cycleStars");
                        starsByUser.put(doc.getId(), s != null ? s : 0);
                    }

                    Map<String, Integer> regionStars = new HashMap<>();
                    final int[] remaining = {userIds.size()};

                    for (String uid : userIds) {
                        db.collection(USERS_COL).document(uid).get()
                                .addOnSuccessListener(userDoc -> {
                                    String region = userDoc.getString("region");
                                    if (region != null) {
                                        long stars = starsByUser.getOrDefault(uid, 0L);
                                        regionStars.merge(region, (int) stars, Integer::sum);
                                    }
                                    remaining[0]--;
                                    if (remaining[0] == 0) cb.onSuccess(regionStars);
                                })
                                .addOnFailureListener(e -> {
                                    remaining[0]--;
                                    if (remaining[0] == 0) cb.onSuccess(regionStars);
                                });
                    }
                })
                .addOnFailureListener(cb::onFailure);
    }

    public static String getPastCycleId(String type) {
        Calendar cal = Calendar.getInstance();
        if (type.equals("weekly")) {
            cal.add(Calendar.WEEK_OF_YEAR, -1);
            int year = cal.get(Calendar.YEAR);
            int week = cal.get(Calendar.WEEK_OF_YEAR);
            return String.format("weekly_%04d_W%02d", year, week);
        } else {
            cal.add(Calendar.MONTH, -1);
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            return String.format("monthly_%04d_%02d", year, month);
        }
    }

    public static class RewardResult {
        public final String userId;
        public final String username;
        public final int rank;
        public final int tokensAwarded;
        public final String cycleType;

        public RewardResult(String userId, String username, int rank, int tokensAwarded, String cycleType) {
            this.userId        = userId;
            this.username      = username;
            this.rank          = rank;
            this.tokensAwarded = tokensAwarded;
            this.cycleType     = cycleType;
        }
    }
}