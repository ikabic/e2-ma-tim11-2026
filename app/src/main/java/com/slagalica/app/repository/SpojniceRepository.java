package com.slagalica.app.repository;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.SpojniceQuestion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpojniceRepository {

    private static final String MATCHES_PATH = "matches";
    private static final String COLLECTION = "spojnice_questions";

    private final FirebaseFirestore db;
    private final DatabaseReference rtdb;

    private DatabaseReference scoreRef;
    private DatabaseReference gameRef;
    private DatabaseReference statsRef;

    private final Map<ValueEventListener, DatabaseReference> activeListeners = new HashMap<>();

    public SpojniceRepository() {
        db = FirebaseFirestore.getInstance();
        rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
    }

    public DatabaseReference getScoreRef() { return scoreRef; }

    public void initMatch(String matchId) {
        scoreRef = rtdb.child(MATCHES_PATH).child(matchId).child("scores").child("1");
        gameRef = scoreRef.child("data");
        statsRef = scoreRef.child("stats");
    }

    public void getRandomQuestions(String seedString, RepositoryCallback<List<SpojniceQuestion>> callback) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<SpojniceQuestion> all = snapshot.toObjects(SpojniceQuestion.class);

                    long seed = seedString.hashCode();
                    java.util.Random rand = new java.util.Random(seed);

                    Collections.shuffle(all, rand);
                    List<SpojniceQuestion> selected = all.subList(0, Math.min(2, all.size()));
                    for (SpojniceQuestion q : selected) {
                        Collections.shuffle(q.getLeftTerms(), new java.util.Random(seed + 1));
                        Collections.shuffle(q.getRightTerms(), new java.util.Random(seed + 2));
                    }

                    callback.onSuccess(selected);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void getQuestionsByIds(List<String> ids, String seedString, RepositoryCallback<List<SpojniceQuestion>> callback) {
        db.collection(COLLECTION)
                .whereIn(FieldPath.documentId(), ids)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, SpojniceQuestion> byId = new HashMap<>();
                    for (SpojniceQuestion q : snapshot.toObjects(SpojniceQuestion.class)) {
                        byId.put(q.getId(), q);
                    }
                    List<SpojniceQuestion> ordered = new ArrayList<>();
                    for (String id : ids) {
                        SpojniceQuestion q = byId.get(id);
                        if (q != null) ordered.add(q);
                    }
                    long seed = seedString.hashCode();
                    for (SpojniceQuestion q : ordered) {
                        Collections.shuffle(q.getLeftTerms(), new java.util.Random(seed + 1));
                        Collections.shuffle(q.getRightTerms(), new java.util.Random(seed + 2));
                    }
                    callback.onSuccess(ordered);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void writeQuestionIds(List<String> ids) {
        gameRef.child("questionIds").setValue(ids);
    }

    public ValueEventListener listenForQuestionIds(RepositoryCallback<List<String>> callback) {
        DatabaseReference ref = gameRef.child("questionIds");
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                if (!snap.exists()) return;
                List<String> ids = new ArrayList<>();
                for (DataSnapshot child : snap.getChildren()) {
                    String id = child.getValue(String.class);
                    if (id != null) ids.add(id);
                }
                if (!ids.isEmpty()) {
                    ref.removeEventListener(this);
                    callback.onSuccess(ids);
                }
            }
            @Override
            public void onCancelled(DatabaseError e) { callback.onFailure(e.toException()); }
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void writeRunningScore(boolean isPlayer1, int score) {
        scoreRef.child(isPlayer1 ? "p1" : "p2").setValue(score);
    }

    public ValueEventListener listenForOpponentScore(boolean isPlayer1, RepositoryCallback<Integer> callback) {
        String key = isPlayer1 ? "p2" : "p1";
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                Integer score = snap.getValue(Integer.class);
                if (score != null) callback.onSuccess(score);
            }
            @Override public void onCancelled(DatabaseError e) { callback.onFailure(e.toException()); }
        };
        addListenerWithTracking(scoreRef.child(key), listener);
        return listener;
    }

    public ValueEventListener listenForOpponentPairs(boolean isPlayer1, int round, RepositoryCallback<Map<Integer, Integer>> callback) {
        String key = pairsKey(!isPlayer1, round);
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Map<Integer, Integer> result = new HashMap<>();
                DataSnapshot pairsSnap = snap.child("pairs");
                for (DataSnapshot child : pairsSnap.getChildren()) {
                    try {
                        int leftIdx = Integer.parseInt(child.getKey());
                        Long rightVal = child.getValue(Long.class);
                        if (rightVal != null) result.put(leftIdx, rightVal.intValue());
                    } catch (NumberFormatException ignored) {}
                }
                callback.onSuccess(result);
            }
            @Override
            public void onCancelled(DatabaseError e) { callback.onFailure(e.toException()); }
        };
        addListenerWithTracking(gameRef.child(key), listener);
        return listener;
    }

    public void writeDone(boolean isPlayer1) {
        gameRef.child(isPlayer1 ? "p1Done" : "p2Done").setValue(true);
    }

    public void writePairs(boolean isPlayer1, int round, Map<Integer, Integer> pairs) {
        if (gameRef == null) return;
        String key = pairsKey(isPlayer1, round);

        Map<String, Object> serializedPairs = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : pairs.entrySet())
            serializedPairs.put(String.valueOf(e.getKey()), e.getValue());

        Map<String, Object> payload = new HashMap<>();
        payload.put("pairs", serializedPairs);
        payload.put("ownerIsP1", isPlayer1);

        gameRef.child(key).setValue(payload);
    }

    public void readOpponentPairs(boolean isPlayer1, int round, RepositoryCallback<Map<Integer, Integer>> callback) {
        String key = pairsKey(!isPlayer1, round);
        gameRef.child(key).get()
                .addOnSuccessListener(snap -> {
                    Map<Integer, Integer> result = new HashMap<>();

                    DataSnapshot pairsSnap = snap.child("pairs");
                    for (DataSnapshot child : pairsSnap.getChildren()) {
                        try {
                            int leftIdx  = Integer.parseInt(child.getKey());
                            Long rightVal = child.getValue(Long.class);
                            if (rightVal != null) result.put(leftIdx, rightVal.intValue());
                        } catch (NumberFormatException ignored) {}
                    }
                    callback.onSuccess(result);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void writeLastGuess(boolean isPlayer1, int rightIdx, boolean isCorrect) {
        if (gameRef == null) return;
        Map<String, Object> guessData = new HashMap<>();
        guessData.put("rightIdx", rightIdx);
        guessData.put("isCorrect", isCorrect);
        guessData.put("timestamp", System.currentTimeMillis());

        gameRef.child(isPlayer1 ? "p1LastGuess" : "p2LastGuess").setValue(guessData);
    }

    public ValueEventListener listenForOpponentGuesses(boolean isPlayer1, RepositoryCallback<Map<String, Object>> callback) {
        if (gameRef == null) return null;
        String targetKey = isPlayer1 ? "p2LastGuess" : "p1LastGuess";
        DatabaseReference ref = gameRef.child(targetKey);

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                if (!snap.exists()) return;
                Map<String, Object> data = new HashMap<>();
                Long rightIdx = snap.child("rightIdx").getValue(Long.class);
                Boolean isCorrect = snap.child("isCorrect").getValue(Boolean.class);
                if (rightIdx != null && isCorrect != null) {
                    data.put("rightIdx", rightIdx.intValue());
                    data.put("isCorrect", isCorrect);
                    callback.onSuccess(data);
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        addListenerWithTracking(ref, listener);
        return listener;
    }

    public void writeActiveLeftIdx(boolean isPlayer1, int leftIdx) {
        if (gameRef == null) return;
        gameRef.child(isPlayer1 ? "p1ActiveLeft" : "p2ActiveLeft").setValue(leftIdx);
    }

    public ValueEventListener listenForOpponentActiveLeft(boolean isPlayer1, RepositoryCallback<Integer> callback) {
        if (gameRef == null) return null;
        String targetKey = isPlayer1 ? "p2ActiveLeft" : "p1ActiveLeft";
        DatabaseReference ref = gameRef.child(targetKey);

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Integer leftIdx = snap.getValue(Integer.class);
                if (leftIdx != null) callback.onSuccess(leftIdx);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        addListenerWithTracking(ref, listener);
        return listener;
    }

    public void writeSubTurnDone(boolean isPlayer1, int round, boolean isLeftover) {
        String key = doneKey(isPlayer1, round, isLeftover);
        gameRef.child(key).setValue(true);
    }

    public ValueEventListener listenForOpponentSubTurnDone(boolean isPlayer1, int round, boolean isLeftoverPhase, Runnable onDone) {
        String key = doneKey(!isPlayer1, round, isLeftoverPhase);
        DatabaseReference ref = gameRef.child(key);
        ValueEventListener listener = new ValueEventListener() {
            private boolean fired = false;
            @Override public void onDataChange(DataSnapshot snap) {
                if (fired) return;
                if (Boolean.TRUE.equals(snap.getValue(Boolean.class))) {
                    fired = true;
                    ref.removeEventListener(this);
                    onDone.run();
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        addListenerWithTracking(ref, listener);
        return listener;
    }

    public ValueEventListener listenForOpponentDone(boolean isPlayer1, Runnable onReady) {
        String doneKey = isPlayer1 ? "p2Done" : "p1Done";
        String forfeitKey = isPlayer1 ? "p2Forfeit" : "p1Forfeit";

        ValueEventListener listener = new ValueEventListener() {
            private boolean fired = false;
            @Override public void onDataChange(DataSnapshot snap) {
                if (fired) return;
                Boolean done = snap.child(doneKey).getValue(Boolean.class);
                Boolean forfeit = snap.child(forfeitKey).getValue(Boolean.class);
                if (Boolean.TRUE.equals(done) || Boolean.TRUE.equals(forfeit)) {
                    fired = true;
                    gameRef.removeEventListener(this);
                    onReady.run();
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        addListenerWithTracking(gameRef, listener);
        return listener;
    }

    private void addListenerWithTracking(DatabaseReference ref, ValueEventListener listener) {
        ref.addValueEventListener(listener);
        activeListeners.put(listener, ref);
    }

    public void removeListener(ValueEventListener listener) {
        if (listener == null) return;
        DatabaseReference ref = activeListeners.remove(listener);
        if (ref != null) {
            ref.removeEventListener(listener);
        }
    }

    private String pairsKey(boolean player1, int round) {
        String prefix = player1 ? "p1" : "p2";
        return prefix + "PairsR" + round;
    }

    private String doneKey(boolean player1, int round, boolean leftover) {
        String prefix = player1 ? "p1" : "p2";
        return prefix + "DoneR" + round + (leftover ? "Leftover" : "");
    }

    public void writeStats(int p1Correct, int p2Correct, int p1Guesses, int p2Guesses, RepositoryCallback<Void> callback) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("p1Correct", p1Correct);
        stats.put("p1Guesses", p1Guesses);
        stats.put("p2Correct", p2Correct);
        stats.put("p2Guesses", p2Guesses);
        statsRef.setValue(stats)
                .addOnSuccessListener(unused -> {
                    if (callback != null) callback.onSuccess(null);
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    public void cleanupMatchData() {
        if (gameRef == null) return;
        gameRef.setValue(null);
    }
}