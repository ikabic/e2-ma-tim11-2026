package com.slagalica.app.repository;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.KoZnaZnaQuestion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KoZnaZnaRepository {

    private static final String MATCHES_PATH = "matches";
    private static final String COLLECTION = "ko_zna_zna_questions";

    private final FirebaseFirestore db;
    private final DatabaseReference rtdb;

    private DatabaseReference scoreRef;
    private DatabaseReference gameRef;
    private DatabaseReference statsRef;

    public KoZnaZnaRepository() {
        db = FirebaseFirestore.getInstance();
        rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
    }

    public void initMatch(String matchId) {
        scoreRef = rtdb.child(MATCHES_PATH).child(matchId).child("scores").child("0");
        gameRef = scoreRef.child("data");
        statsRef = scoreRef.child("stats");
    }

    public void getRandomQuestions(RepositoryCallback<List<KoZnaZnaQuestion>> callback) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<KoZnaZnaQuestion> all = snapshot.toObjects(KoZnaZnaQuestion.class);
                    Collections.shuffle(all);
                    callback.onSuccess(all);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void getQuestionsByIds(List<String> ids, RepositoryCallback<List<KoZnaZnaQuestion>> callback) {
        db.collection(COLLECTION)
                .whereIn(FieldPath.documentId(), ids)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, KoZnaZnaQuestion> byId = new HashMap<>();
                    for (KoZnaZnaQuestion q : snapshot.toObjects(KoZnaZnaQuestion.class)) {
                        byId.put(q.getId(), q);
                    }
                    List<KoZnaZnaQuestion> ordered = new ArrayList<>();
                    for (String id : ids) {
                        KoZnaZnaQuestion q = byId.get(id);
                        if (q != null) ordered.add(q);
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

    public void removeQuestionIdsListener(ValueEventListener listener) {
        gameRef.child("questionIds").removeEventListener(listener);
    }

    public void writeRunningScore(boolean isPlayer1, int score) {
        scoreRef.child(isPlayer1 ? "p1" : "p2").setValue(score);
    }

    public void readOpponentAnswerOnce(boolean isPlayer1, int questionIndex, RepositoryCallback<long[]> callback) {
        String oppPrefix = isPlayer1 ? "p2" : "p1";
        String answerKey = oppPrefix + "Answer" + questionIndex;
        String timeKey = oppPrefix + "Time" + questionIndex;

        gameRef.get()
                .addOnSuccessListener(snap -> {
                    DataSnapshot ansSnap  = snap.child(answerKey);
                    DataSnapshot timeSnap = snap.child(timeKey);
                    if (!ansSnap.exists() || !timeSnap.exists()) {
                        callback.onFailure(new Exception("No opponent answer yet"));
                        return;
                    }
                    Long answerRaw = ansSnap.getValue(Long.class);
                    Long timeRaw   = timeSnap.getValue(Long.class);
                    if (answerRaw == null || timeRaw == null) {
                        callback.onFailure(new Exception("No opponent answer yet"));
                        return;
                    }
                    callback.onSuccess(new long[]{answerRaw, timeRaw});
                })
                .addOnFailureListener(callback::onFailure);
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
        scoreRef.child(key).addValueEventListener(listener);
        return listener;
    }

    public void removeOpponentScoreListener(ValueEventListener listener) {
        if (gameRef != null) gameRef.removeEventListener(listener);
    }

    public void writeAnswer(boolean isPlayer1, int questionIndex, int answerIndex, long elapsedMs) {
        String prefix = isPlayer1 ? "p1" : "p2";
        Map<String, Object> update = new HashMap<>();
        update.put(prefix + "Answer" + questionIndex, answerIndex);
        update.put(prefix + "Time" + questionIndex, elapsedMs);
        gameRef.updateChildren(update);
    }

    public void writeDone(boolean isPlayer1) {
        gameRef.child(isPlayer1 ? "p1Done" : "p2Done").setValue(true);
    }

    public ValueEventListener listenForOpponentReady(boolean isPlayer1, Runnable onReady) {
        String doneKey = isPlayer1 ? "p2Done" : "p1Done";
        String forfeitKey = isPlayer1 ? "p2Forfeit" : "p1Forfeit";

        ValueEventListener listener = new ValueEventListener() {
            private boolean fired = false;
            @Override
            public void onDataChange(DataSnapshot snap) {
                if (fired) return;
                Boolean done = snap.child(doneKey).getValue(Boolean.class);
                Boolean forfeit = snap.child(forfeitKey).getValue(Boolean.class);
                if (Boolean.TRUE.equals(done) || Boolean.TRUE.equals(forfeit)) {
                    fired = true;
                    gameRef.removeEventListener(this);
                    onReady.run();
                }
            }
            @Override
            public void onCancelled(DatabaseError e) {}
        };
        gameRef.addValueEventListener(listener);
        return listener;
    }

    public void removeOpponentReadyListener(ValueEventListener listener) {
        gameRef.removeEventListener(listener);
    }

    public void removeOpponentAnswersListener(ValueEventListener listener) {
        if (gameRef != null) gameRef.removeEventListener(listener);
    }

    public void readAllAnswers(RepositoryCallback<DataSnapshot> callback) {
        gameRef.get()
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onFailure);
    }

    public void writeStats(int p1Correct, int p1Wrong, int p2Correct, int p2Wrong, RepositoryCallback<Void> callback) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("p1Correct", p1Correct);
        stats.put("p1Wrong", p1Wrong);
        stats.put("p2Correct", p2Correct);
        stats.put("p2Wrong", p2Wrong);
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