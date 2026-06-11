package com.slagalica.app.repository;

import android.util.Pair;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.slagalica.app.model.GameStatistics;
import com.slagalica.app.model.PlayerStatistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatisticsRepository {

    private static final String MATCHES = "matches";
    private static final String STATS = "statistics";
    private static final String GAMES = "games";

    private final FirebaseFirestore db;
    private final DatabaseReference rtdb;

    public StatisticsRepository() {
        db = FirebaseFirestore.getInstance();
        rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
    }

    private static final Map<Integer, Pair<Integer, Integer>> GAME_CONFIG_MAP = new HashMap<>(); // <gameId, <min, max>> points
    static {
        GAME_CONFIG_MAP.put(0, new Pair<>(-25, 50));
        GAME_CONFIG_MAP.put(1, new Pair<>(0, 20));
        GAME_CONFIG_MAP.put(2, new Pair<>(0, 60));
        GAME_CONFIG_MAP.put(3, new Pair<>(0, 40));
        GAME_CONFIG_MAP.put(4, new Pair<>(0, 40));
        GAME_CONFIG_MAP.put(5, new Pair<>(0, 20));
    }

    public void updateStats(String matchId, String uid, int myScore, int opponentScore, RepositoryCallback<Void> callback) {
        rtdb.child(MATCHES).child(matchId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                if (callback != null) callback.onFailure(new Exception("Match data not found in RTDB"));
                return;
            }

            String p1Uid = snapshot.child("p1Uid").getValue(String.class);
            String p2Uid = snapshot.child("p2Uid").getValue(String.class);

            if (p1Uid == null || p2Uid == null) {
                if (callback != null) callback.onFailure(new Exception("One or both player UIDs are missing"));
                return;
            }

            boolean isPlayer1 = uid.equals(p1Uid);

            int p1MatchScore = isPlayer1 ? myScore : opponentScore;
            int p2MatchScore = isPlayer1 ? opponentScore : myScore;

            WriteBatch batch = db.batch();

            Map<String, Object> p1MatchUpdates = new HashMap<>();
            Map<String, Object> p2MatchUpdates = new HashMap<>();

            p1MatchUpdates.put("matchesPlayed", FieldValue.increment(1));
            p1MatchUpdates.put("totalScore", FieldValue.increment(p1MatchScore));

            p2MatchUpdates.put("matchesPlayed", FieldValue.increment(1));
            p2MatchUpdates.put("totalScore", FieldValue.increment(p2MatchScore));

            if (p1MatchScore > p2MatchScore) {
                p1MatchUpdates.put("matchesWon", FieldValue.increment(1));
                p2MatchUpdates.put("matchesLost", FieldValue.increment(1));
            } else if (p1MatchScore < p2MatchScore) {
                p1MatchUpdates.put("matchesLost", FieldValue.increment(1));
                p2MatchUpdates.put("matchesWon", FieldValue.increment(1));
            } else {
                p1MatchUpdates.put("matchesDraw", FieldValue.increment(1));
                p2MatchUpdates.put("matchesDraw", FieldValue.increment(1));
            }

            var p1MainDocRef = db.collection(STATS).document(p1Uid);
            var p2MainDocRef = db.collection(STATS).document(p2Uid);
            batch.set(p1MainDocRef, p1MatchUpdates, SetOptions.merge());
            batch.set(p2MainDocRef, p2MatchUpdates, SetOptions.merge());

            DataSnapshot scoresSnap = snapshot.child("scores");
            for (DataSnapshot gameSnap : scoresSnap.getChildren()) {
                String gameId = gameSnap.getKey();
                if (gameId == null) continue;

                Long p1GameScoreObj = gameSnap.child("p1").getValue(Long.class);
                Long p2GameScoreObj = gameSnap.child("p2").getValue(Long.class);

                int p1GameScore = p1GameScoreObj != null ? p1GameScoreObj.intValue() : -999;
                int p2GameScore = p2GameScoreObj != null ? p2GameScoreObj.intValue() : -999;

                if (p1GameScore == -999 && p2GameScore == -999) continue;

                Map<String, Object> p1GameUpdates = new HashMap<>();
                p1GameUpdates.put("totalPlayed", FieldValue.increment(1));
                p1GameUpdates.put("totalPoints", FieldValue.increment(p1GameScore));

                Map<String, Object> p2GameUpdates = new HashMap<>();
                p2GameUpdates.put("totalPlayed", FieldValue.increment(1));
                p2GameUpdates.put("totalPoints", FieldValue.increment(p2GameScore));

                DataSnapshot statsSnap = gameSnap.child(STATS);
                if (statsSnap.exists()) {
                    for (DataSnapshot statChild : statsSnap.getChildren()) {
                        String statKey = statChild.getKey();
                        if (statKey == null) continue;

                        String prefix = null;
                        Map<String, Object> targetGameUpdates = null;

                        if (statKey.startsWith("p1")) {
                            prefix = "p1";
                            targetGameUpdates = p1GameUpdates;
                        } else if (statKey.startsWith("p2")) {
                            prefix = "p2";
                            targetGameUpdates = p2GameUpdates;
                        }

                        if (prefix != null) {
                            String rawKey = statKey.substring(prefix.length());
                            if (!rawKey.isEmpty()) {
                                String cleanKey = rawKey.substring(0, 1).toLowerCase() + rawKey.substring(1);
                                Long statVal = statChild.getValue(Long.class);

                                if (statVal != null) {
                                    String targetField = mapKeyToFieldName(cleanKey);
                                    targetGameUpdates.put(targetField, FieldValue.increment(statVal));
                                }
                            }
                        }
                    }
                }

                var p1GameDocRef = db.collection(STATS).document(p1Uid).collection(GAMES).document(gameId);
                var p2GameDocRef = db.collection(STATS).document(p2Uid).collection(GAMES).document(gameId);

                batch.set(p1GameDocRef, p1GameUpdates, SetOptions.merge());
                batch.set(p2GameDocRef, p2GameUpdates, SetOptions.merge());
            }

            batch.commit()
                    .addOnSuccessListener(v -> { if (callback != null) callback.onSuccess(null); })
                    .addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });

        }).addOnFailureListener(fCallback -> {
            if (callback != null) callback.onFailure(fCallback);
        });
    }

    private String mapKeyToFieldName(String cleanKey) {
        switch (cleanKey) {
            case "correct": return "correctGuesses";
            case "wrong": return "wrongGuesses";
            case "guesses": return "guessesMade";
            default: return cleanKey;
        }
    }

    public void loadStats(String uid, RepositoryCallback<PlayerStatistics> callback) {
        db.collection(STATS).document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onSuccess(new PlayerStatistics(0, new Pair<>(0, 0), getDefaultGames()));
                        return;
                    }

                    int played = toInt(doc.getLong("matchesPlayed"));
                    int won = toInt(doc.getLong("matchesWon"));
                    int lost = toInt(doc.getLong("matchesLost"));

                    db.collection(STATS).document(uid).collection(GAMES).get()
                            .addOnSuccessListener(querySnapshot -> {
                                Map<Integer, GameStatistics> completeGamesMap = new HashMap<>();
                                for (GameStatistics defaultGs : getDefaultGames())
                                    completeGamesMap.put(defaultGs.getGameId(), defaultGs);

                                for (QueryDocumentSnapshot gameDoc : querySnapshot) {
                                    try {
                                        int gameId = Integer.parseInt(gameDoc.getId());
                                        int totalPlayed = toInt(gameDoc.getLong("totalPlayed"));
                                        int totalPoints = toInt(gameDoc.getLong("totalPoints"));

                                        Pair<Integer, Integer> bounds = GAME_CONFIG_MAP.getOrDefault(gameId, new Pair<>(0, 20));
                                        int maxPoints = bounds.second;
                                        int minPoints = bounds.first;

                                        GameStatistics gs = new GameStatistics(gameId, totalPlayed, totalPoints, maxPoints, minPoints);

                                        Map<String, Object> data = gameDoc.getData();
                                        Map<String, Long> metrics = new HashMap<>();
                                        for (Map.Entry<String, Object> entry : data.entrySet()) {
                                            if (entry.getValue() instanceof Long)
                                                metrics.put(entry.getKey(), (Long) entry.getValue());
                                        }
                                        gs.setDynamicFields(metrics);
                                        completeGamesMap.put(gameId, gs);
                                    } catch (NumberFormatException ignored) {}
                                }
                                List<GameStatistics> gamesList = new ArrayList<>();
                                for (int i = 0; i <= 5; i++) gamesList.add(completeGamesMap.get(i));

                                callback.onSuccess(new PlayerStatistics(played, new Pair<>(won, lost), gamesList));
                            })
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private List<GameStatistics> getDefaultGames() {
        List<GameStatistics> defaultList = new ArrayList<>();
        for (int gameId = 0; gameId <= 5; gameId++) {
            Pair<Integer, Integer> bounds = GAME_CONFIG_MAP.getOrDefault(gameId, new Pair<>(0, 20));
            int minPoints = bounds.first;
            int maxPoints = bounds.second;

            GameStatistics gs = new GameStatistics(gameId, 0, 0, maxPoints, minPoints);
            gs.setDynamicFields(new HashMap<>());
            defaultList.add(gs);
        }
        return defaultList;
    }

    private int toInt(Long val) { return val != null ? val.intValue() : 0; }
}