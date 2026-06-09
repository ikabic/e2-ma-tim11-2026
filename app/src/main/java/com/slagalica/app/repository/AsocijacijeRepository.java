package com.slagalica.app.repository;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.AsocijacijeQuestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsocijacijeRepository {

    private static final String MATCHES_PATH = "matches";
    private static final String COLLECTION = "asocijacije_questions";

    private final FirebaseFirestore db;
    private final DatabaseReference rtdb;

    private DatabaseReference matchRef;
    private DatabaseReference gameRef;
    private DatabaseReference statsRef;

    private final Map<ValueEventListener, DatabaseReference> activeListeners = new HashMap<>();

    public AsocijacijeRepository() {
        db = FirebaseFirestore.getInstance();
        rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
    }

    public void initMatch(String matchId) {
        matchRef = rtdb.child(MATCHES_PATH).child(matchId);
        setRound(1);
    }

    public void setRound(int roundNumber) {
        for (Map.Entry<ValueEventListener, DatabaseReference> entry : activeListeners.entrySet()) {
            entry.getValue().removeEventListener(entry.getKey());
        }
        activeListeners.clear();

        if (matchRef != null) {
            gameRef  = matchRef.child("asocijacije").child("round_" + roundNumber);
            statsRef = matchRef.child("asocijacije").child("stats");
            if (roundNumber > 1) {
                matchRef.child("asocijacije").child("round_" + (roundNumber - 1) + "_state").setValue(null);
            }
        }
    }

    public void getRandomQuestion(RepositoryCallback<AsocijacijeQuestion> callback) {
        db.collection(COLLECTION).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        seedTestData();
                        callback.onFailure(new Exception("No questions — seeding now, retry."));
                        return;
                    }
                    int idx = (int) (Math.random() * snapshot.size());
                    AsocijacijeQuestion q = snapshot.getDocuments().get(idx)
                            .toObject(AsocijacijeQuestion.class);
                    if (q != null) q.setId(snapshot.getDocuments().get(idx).getId());
                    callback.onSuccess(q);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public com.google.firebase.database.DatabaseReference getGameRef() {
        return this.gameRef;
    }

    public void getQuestionById(String id, RepositoryCallback<AsocijacijeQuestion> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onFailure(new Exception("Not found")); return; }
                    AsocijacijeQuestion q = doc.toObject(AsocijacijeQuestion.class);
                    if (q != null) q.setId(doc.getId());
                    callback.onSuccess(q);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void writeQuestionId(String questionId) {
        gameRef.child("questionId").setValue(questionId);
    }

    public ValueEventListener listenForQuestionId(RepositoryCallback<String> callback) {
        DatabaseReference ref = gameRef.child("questionId");
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                String qId = snap.getValue(String.class);
                if (qId != null && !qId.isEmpty()) {
                    ref.removeEventListener(this);
                    callback.onSuccess(qId);
                }
            }
            @Override public void onCancelled(DatabaseError e) { callback.onFailure(e.toException()); }
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void writeActivePlayer(boolean isP1Turn) {
        gameRef.child("activePlayer").setValue(isP1Turn ? "p1" : "p2");
    }

    public ValueEventListener listenForActivePlayer(RepositoryCallback<Boolean> isP1TurnCallback) {
        DatabaseReference ref = gameRef.child("activePlayer");
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                String val = snap.getValue(String.class);
                if (val != null) isP1TurnCallback.onSuccess("p1".equals(val));
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        addTracked(ref, listener);
        return listener;
    }

    public void writeFieldOpened(int col, int field) {
        gameRef.child("board").child(col + "_" + field).setValue(true);
    }

    public ValueEventListener listenForBoard(RepositoryCallback<Map<String, Boolean>> callback) {
        DatabaseReference ref = gameRef.child("board");
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                Map<String, Boolean> board = new HashMap<>();
                for (DataSnapshot child : snap.getChildren()) {
                    Boolean v = child.getValue(Boolean.class);
                    if (v != null) board.put(child.getKey(), v);
                }
                callback.onSuccess(board);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        addTracked(ref, listener);
        return listener;
    }

    public void writeColumnSolved(int col) {
        gameRef.child("columnSolved").child(String.valueOf(col)).setValue(true);
    }

    public void writeFinalSolved(boolean solved) {
        gameRef.child("finalSolved").setValue(solved);
    }

    public ValueEventListener listenForSolvedState(SolvedStateCallback callback) {
        DatabaseReference ref = gameRef;
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                boolean[] cols = new boolean[4];
                DataSnapshot cs = snap.child("columnSolved");
                for (int i = 0; i < 4; i++) {
                    Boolean v = cs.child(String.valueOf(i)).getValue(Boolean.class);
                    cols[i] = Boolean.TRUE.equals(v);
                }
                Boolean finalSolved = snap.child("finalSolved").getValue(Boolean.class);
                callback.onUpdate(cols, Boolean.TRUE.equals(finalSolved));
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        addTracked(ref, listener);
        return listener;
    }

    public void writeLastAction(String type, int col, boolean correct, String answer) {
        Map<String, Object> action = new HashMap<>();
        action.put("type", type);
        action.put("col", col);
        action.put("correct", correct);
        action.put("answer", answer);
        action.put("ts", System.currentTimeMillis());
        gameRef.child("lastAction").setValue(action);
    }

    public ValueEventListener listenForLastAction(RepositoryCallback<Map<String, Object>> callback) {
        DatabaseReference ref = gameRef.child("lastAction");
        ValueEventListener listener = new ValueEventListener() {
            private long lastTs = 0;
            @Override public void onDataChange(DataSnapshot snap) {
                if (!snap.exists()) return;
                Map<String, Object> action = new HashMap<>();
                for (DataSnapshot child : snap.getChildren())
                    action.put(child.getKey(), child.getValue());
                Long ts = snap.child("ts").getValue(Long.class);
                if (ts != null && ts > lastTs) {
                    lastTs = ts;
                    callback.onSuccess(action);
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        addTracked(ref, listener);
        return listener;
    }

    public void writeScore(boolean isPlayer1, int score) {
        if (gameRef != null) {
            gameRef.child(isPlayer1 ? "p1Score" : "p2Score").setValue(score);
        }
    }

    public ValueEventListener listenForOpponentScore(boolean isPlayer1, RepositoryCallback<Integer> callback) {
        String key = isPlayer1 ? "p2Score" : "p1Score";
        DatabaseReference ref = gameRef.child(key);
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                Integer v = snap.getValue(Integer.class);
                if (v != null) callback.onSuccess(v);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        addTracked(ref, listener);
        return listener;
    }

    public void writeDone(boolean isPlayer1) {
        gameRef.child(isPlayer1 ? "p1Done" : "p2Done").setValue(true);
    }

    public ValueEventListener listenForOpponentDone(boolean isPlayer1, Runnable onDone) {
        String key = isPlayer1 ? "p2Done" : "p1Done";
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
        ref.addValueEventListener(listener);
        return listener;
    }

    public void writeStats(int p1ColsSolved, boolean p1FinalSolved, int p1Score, int p2ColsSolved, boolean p2FinalSolved, int p2Score, RepositoryCallback<Void> callback) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("p1ColsSolved", p1ColsSolved);
        stats.put("p1FinalSolved", p1FinalSolved);
        stats.put("p1Score", p1Score);
        stats.put("p2ColsSolved", p2ColsSolved);
        stats.put("p2FinalSolved", p2FinalSolved);
        stats.put("p2Score", p2Score);
        statsRef.setValue(stats).addOnSuccessListener(v -> { if (callback != null) callback.onSuccess(null); })
                .addOnFailureListener(e -> { if (callback != null) callback.onFailure(e); });
    }


    private void addTracked(DatabaseReference ref, ValueEventListener l) {
        ref.addValueEventListener(l);
        activeListeners.put(l, ref);
    }

    public void removeListener(ValueEventListener l) {
        if (l == null) return;
        DatabaseReference ref = activeListeners.remove(l);
        if (ref != null) ref.removeEventListener(l);
    }

    public void cleanupMatchData() {
        if (gameRef != null) gameRef.child("board").setValue(null);
    }

    public void seedTestData() {
        AsocijacijeQuestion q1 = new AsocijacijeQuestion(null,
                java.util.Arrays.asList("Red", "Largest", "Gas giant", "Jupiter"),
                java.util.Arrays.asList("Rocky", "Closest to Sun", "Craters", "Mercury"),
                java.util.Arrays.asList("Rings", "Saturn", "Ice", "Cassini"),
                java.util.Arrays.asList("Moon", "Life", "Blue", "Water"),
                java.util.Arrays.asList("Gas Giants", "Rocky Planets", "Ringed Planets", "Earth-like"),
                "Planets");
        AsocijacijeQuestion q2 = new AsocijacijeQuestion(null,
                java.util.Arrays.asList("Federer", "Wimbledon", "Racket", "Serve"),
                java.util.Arrays.asList("Messi", "Goal", "Pitch", "Dribble"),
                java.util.Arrays.asList("Michael Jordan", "Slam dunk", "Hoop", "NBA"),
                java.util.Arrays.asList("Phelps", "Pool", "Butterfly", "Stroke"),
                java.util.Arrays.asList("Tennis", "Football", "Basketball", "Swimming"),
                "Sports");
        for (AsocijacijeQuestion q : java.util.Arrays.asList(q1, q2))
            db.collection(COLLECTION).add(q);
    }

    public interface SolvedStateCallback {
        void onUpdate(boolean[] columnsSolved, boolean finalSolved);
    }

    public void writeRoundStartTime(int round) {
        if (matchRef == null) return;
        DatabaseReference ref = matchRef.child("asocijacije").child("round_" + round + "_startTime");
        ref.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(
                    com.google.firebase.database.MutableData data) {
                if (data.getValue() == null) {
                    data.setValue(com.google.firebase.database.ServerValue.TIMESTAMP);
                }
                return com.google.firebase.database.Transaction.success(data);
            }
            @Override public void onComplete(DatabaseError e, boolean committed, DataSnapshot s) {}
        });
    }

    public ValueEventListener listenForRoundStartTime(int round, RepositoryCallback<Long> callback) {
        if (matchRef == null) return null;
        DatabaseReference ref = matchRef.child("asocijacije").child("round_" + round + "_startTime");
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                Long ts = snap.getValue(Long.class);
                if (ts != null && ts > 0) callback.onSuccess(ts);
            }
            @Override public void onCancelled(DatabaseError e) { callback.onFailure(e.toException()); }
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void removeRoundStartTimeListener(int round, ValueEventListener listener) {
        if (matchRef == null || listener == null) return;
        matchRef.child("asocijacije").child("round_" + round + "_startTime").removeEventListener(listener);
    }

    public void writeInitialScores(int p1Prev, int p2Prev) {
        if (matchRef == null) return;
        Map<String, Object> scores = new HashMap<>();
        scores.put("p1Prev", p1Prev);
        scores.put("p2Prev", p2Prev);
        matchRef.child("asocijacije").child("initialScores").setValue(scores);
    }

    public ValueEventListener listenForInitialScores(RepositoryCallback<int[]> callback) {
        DatabaseReference ref = matchRef.child("asocijacije").child("initialScores");
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                if (!snap.exists()) return;
                Long p1 = snap.child("p1Prev").getValue(Long.class);
                Long p2 = snap.child("p2Prev").getValue(Long.class);
                if (p1 != null && p2 != null) {
                    callback.onSuccess(new int[]{p1.intValue(), p2.intValue()});
                }
            }
            @Override public void onCancelled(DatabaseError e) { callback.onFailure(e.toException()); }
        };
        addTracked(ref, listener);
        return listener;
    }
}