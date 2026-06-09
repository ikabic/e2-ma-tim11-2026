package com.slagalica.app.repository;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.SkockoQuestion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SkockoRepository {

    private static final String MATCHES_PATH = "matches";
    private static final String COLLECTION   = "skocko_questions";

    private final FirebaseFirestore db;
    private final DatabaseReference rtdb;

    private DatabaseReference gameRef;
    private DatabaseReference statsRef;

    private final Map<ValueEventListener, DatabaseReference> activeListeners = new HashMap<>();

    public SkockoRepository() {
        db   = FirebaseFirestore.getInstance();
        rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
    }

    public void initMatch(String matchId) {
        gameRef  = rtdb.child(MATCHES_PATH).child(matchId).child("scores").child("3");
        statsRef = gameRef.child("stats");
    }

    public void getRandomQuestion(RepositoryCallback<SkockoQuestion> callback) {
        db.collection(COLLECTION).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        seedTestData();
                        callback.onFailure(new Exception("No questions — seeding, retry."));
                        return;
                    }
                    int idx = (int) (Math.random() * snapshot.size());
                    SkockoQuestion q = snapshot.getDocuments().get(idx)
                            .toObject(SkockoQuestion.class);
                    if (q != null) q.setId(snapshot.getDocuments().get(idx).getId());
                    callback.onSuccess(q);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void getQuestionById(String id, RepositoryCallback<SkockoQuestion> callback) {
        db.collection(COLLECTION).document(id).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onFailure(new Exception("Not found")); return; }
                    SkockoQuestion q = doc.toObject(SkockoQuestion.class);
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

    public ValueEventListener listenForActivePlayer(RepositoryCallback<Boolean> callback) {
        DatabaseReference ref = gameRef.child("activePlayer");
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                String val = snap.getValue(String.class);
                if (val != null) callback.onSuccess("p1".equals(val));
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        addTracked(ref, listener);
        return listener;
    }

    public void writeGuessRow(int round, int attempt, List<Integer> symbols, List<String> results) {
        Map<String, Object> row = new HashMap<>();
        row.put("symbols", symbols);
        row.put("results", results);
        gameRef.child("round" + round).child("guesses")
                .child(String.valueOf(attempt)).setValue(row);
    }

    public ValueEventListener listenForGuessHistory(int round, RepositoryCallback<List<GuessRow>> callback) {
        DatabaseReference ref = gameRef.child("round" + round).child("guesses");
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                List<GuessRow> rows = new ArrayList<>();
                for (DataSnapshot child : snap.getChildren()) {
                    List<Long> symsRaw = new ArrayList<>();
                    for (DataSnapshot s : child.child("symbols").getChildren())
                        symsRaw.add(s.getValue(Long.class));
                    List<Integer> syms = new ArrayList<>();
                    for (Long l : symsRaw) syms.add(l != null ? l.intValue() : 0);

                    List<String> results = new ArrayList<>();
                    for (DataSnapshot r : child.child("results").getChildren())
                        results.add(r.getValue(String.class));

                    rows.add(new GuessRow(syms, results));
                }
                callback.onSuccess(rows);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        addTracked(ref, listener);
        return listener;
    }

    public void writeBonusActive(int round, boolean active) {
        gameRef.child("round" + round).child("bonusActive").setValue(active);
    }

    public ValueEventListener listenForBonusActive(int round, Runnable onBonus) {
        DatabaseReference ref = gameRef.child("round" + round).child("bonusActive");
        ValueEventListener listener = new ValueEventListener() {
            private boolean fired = false;
            @Override public void onDataChange(DataSnapshot snap) {
                if (fired) return;
                if (Boolean.TRUE.equals(snap.getValue(Boolean.class))) {
                    fired = true;
                    ref.removeEventListener(this);
                    onBonus.run();
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void writeRoundDone(int round, boolean isPlayer1) {
        gameRef.child("round" + round).child(isPlayer1 ? "p1Done" : "p2Done").setValue(true);
    }

    public ValueEventListener listenForRoundDone(int round, boolean isPlayer1, Runnable onDone) {
        String key = isPlayer1 ? "p1Done" : "p2Done";
        DatabaseReference ref = gameRef.child("round" + round).child(key);
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

    public void writeScore(boolean isPlayer1, int score) {
        gameRef.child(isPlayer1 ? "p1Score" : "p2Score").setValue(score);
    }

    public ValueEventListener listenForOpponentScore(boolean isPlayer1,
                                                     RepositoryCallback<Integer> callback) {
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

    public void writeStats(boolean p1SolvedR1, int p1AttemptR1, boolean p1SolvedR2, int p1AttemptR2, boolean p2SolvedR1, int p2AttemptR1, boolean p2SolvedR2, int p2AttemptR2, int p1Score, int p2Score, RepositoryCallback<Void> callback) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("p1SolvedR1",  p1SolvedR1);
        stats.put("p1AttemptR1", p1AttemptR1);
        stats.put("p1SolvedR2",  p1SolvedR2);
        stats.put("p1AttemptR2", p1AttemptR2);
        stats.put("p2SolvedR1",  p2SolvedR1);
        stats.put("p2AttemptR1", p2AttemptR1);
        stats.put("p2SolvedR2",  p2SolvedR2);
        stats.put("p2AttemptR2", p2AttemptR2);
        stats.put("p1Score", p1Score);
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
        if (gameRef != null) gameRef.setValue(null);
    }

    public void seedTestData() {
        SkockoQuestion[] qs = {
                new SkockoQuestion(null, Arrays.asList(0, 1, 2, 3)),
                new SkockoQuestion(null, Arrays.asList(5, 5, 1, 4)),
                new SkockoQuestion(null, Arrays.asList(3, 0, 5, 2)),
                new SkockoQuestion(null, Arrays.asList(4, 3, 3, 0)),
                new SkockoQuestion(null, Arrays.asList(2, 4, 1, 5)),
        };
        for (SkockoQuestion q : qs) db.collection(COLLECTION).add(q);
    }

    public static class GuessRow {
        public final List<Integer> symbols;
        public final List<String>  results;
        public GuessRow(List<Integer> symbols, List<String> results) {
            this.symbols = symbols;
            this.results = results;
        }
    }
}