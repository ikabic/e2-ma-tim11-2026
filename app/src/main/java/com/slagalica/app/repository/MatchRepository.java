package com.slagalica.app.repository;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.util.UserStatusManager;

import java.util.HashMap;
import java.util.Map;

public class MatchRepository {

    private static final String QUEUE_PATH   = "matchmaking_queue";
    private static final String MATCHES_PATH = "matches";
    private static final int    RETRY_DELAY_MS = 4000;

    private final DatabaseReference rtdb;
    private final FirebaseFirestore  db;
    private final FirebaseAuth       auth;

    public interface MatchFoundCallback {
        void onMatchFound(String matchId, boolean isPlayer1, String opponentUsername);
    }

    public interface ScoreCallback {
        void onScoreReady(int p1Score, int p2Score, boolean bothDone);
    }

    public interface Game4ResolvedCallback {
        void onResolved(int finalP1, int p2);
    }

    public interface MojBrojIntCallback {
        void onValue(int value);
    }

    public interface MojBrojNumbersCallback {
        void onValue(int[] numbers);
    }

    public interface MojBrojResultsCallback {
        void onValues(Integer p1Result, Integer p2Result);
    }

    public MatchRepository() {
        rtdb = FirebaseDatabase.getInstance(
            "https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public String getUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    public void joinQueue(String username, MatchFoundCallback matchCallback,
                          RepositoryCallback<Void> errCallback) {
        String uid = getUid();
        if (uid == null) { errCallback.onFailure(new Exception("Not logged in")); return; }

        Map<String, Object> entry = new HashMap<>();
        entry.put("uid",       uid);
        entry.put("username",  username);
        entry.put("timestamp", System.currentTimeMillis());
        entry.put("matchId",   "");

        DatabaseReference myRef = rtdb.child(QUEUE_PATH).child(uid);
        myRef.onDisconnect().removeValue();
        myRef.setValue(entry)
            .addOnSuccessListener(unused -> scanQueue(uid, username, matchCallback, errCallback))
            .addOnFailureListener(errCallback::onFailure);
    }

    private void scanQueue(String myUid, String myUsername,
                           MatchFoundCallback matchCallback, RepositoryCallback<Void> errCallback) {
        rtdb.child(QUEUE_PATH).orderByChild("matchId").equalTo("")
            .get()
            .addOnSuccessListener(snap -> {
                DataSnapshot opponent = null;
                for (DataSnapshot child : snap.getChildren()) {
                    if (!myUid.equals(child.getKey())) { opponent = child; break; }
                }
                if (opponent == null) {
                    listenForMatch(myUid, matchCallback);
                } else {
                    tryClaimOpponent(opponent, myUid, myUsername, matchCallback, errCallback);
                }
            })
            .addOnFailureListener(errCallback::onFailure);
    }

    private void tryClaimOpponent(DataSnapshot opponent, String myUid, String myUsername,
                                   MatchFoundCallback matchCallback, RepositoryCallback<Void> errCallback) {
        String opUid      = opponent.getKey();
        Object uObj       = opponent.child("username").getValue();
        String opUsername = uObj != null ? uObj.toString() : "Opponent";
        String newMatchId = rtdb.child(MATCHES_PATH).push().getKey();

        rtdb.child(QUEUE_PATH).child(opUid).runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData data) {
                Object existing = data.child("matchId").getValue();
                if (existing == null || !existing.toString().isEmpty()) {
                    return Transaction.abort();
                }
                data.child("matchId").setValue(newMatchId);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot current) {
                if (!committed) {
                    listenForMatch(myUid, matchCallback);
                    return;
                }
                createMatch(newMatchId, myUid, myUsername, opUid, opUsername, matchCallback, errCallback);
            }
        });
    }

    private void createMatch(String matchId,
                              String p1Uid, String p1Username,
                              String p2Uid, String p2Username,
                              MatchFoundCallback matchCallback, RepositoryCallback<Void> errCallback) {
        Map<String, Object> match = new HashMap<>();
        match.put("p1Uid",      p1Uid);
        match.put("p1Username", p1Username);
        match.put("p2Uid",      p2Uid);
        match.put("p2Username", p2Username);
        match.put("status",     "active");

        Map<String, Object> scores = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            Map<String, Object> gs = new HashMap<>();
            gs.put("p1", -999);
            gs.put("p2", -999);
            gs.put("p1Done", false);
            gs.put("p2Done", false);
            scores.put(String.valueOf(i), gs);
        }
        match.put("scores", scores);

        rtdb.child(MATCHES_PATH).child(matchId).setValue(match)
            .addOnSuccessListener(unused -> {
                rtdb.child(QUEUE_PATH).child(p1Uid).removeValue();
                matchCallback.onMatchFound(matchId, true, p2Username);
            })
            .addOnFailureListener(errCallback::onFailure);
    }

    public void createInviteMatch(String inviteId, String player1Uid, String player2Uid, String player1Username, String player2Username, RepositoryCallback<String> callback) {
        String matchId = rtdb.child(MATCHES_PATH).push().getKey();

        Map<String, Object> match = new HashMap<>();
        match.put("p1Uid", player1Uid);
        match.put("p1Username", player1Username);
        match.put("p2Uid", player2Uid);
        match.put("p2Username", player2Username);
        match.put("status", "active");
        match.put("invite", true);

        Map<String, Object> scores = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            Map<String, Object> gs = new HashMap<>();
            gs.put("p1", -999);
            gs.put("p2", -999);
            gs.put("p1Done", false);
            gs.put("p2Done", false);
            scores.put(String.valueOf(i), gs);
        }
        match.put("scores", scores);

        rtdb.child(MATCHES_PATH).child(matchId).setValue(match)
                .addOnSuccessListener(unused -> {
                    rtdb.child("invites").child(inviteId).child("matchId").setValue(matchId)
                            .addOnSuccessListener(v -> callback.onSuccess(matchId))
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private void listenForMatch(String uid, MatchFoundCallback matchCallback) {
        DatabaseReference matchIdRef = rtdb.child(QUEUE_PATH).child(uid).child("matchId");
        final boolean[] resolved = {false};

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Object val = snap.getValue();
                if (val == null || val.toString().isEmpty()) return;
                resolved[0] = true;
                String matchId = val.toString();
                matchIdRef.removeEventListener(this);
                rtdb.child(QUEUE_PATH).child(uid).removeValue();
                rtdb.child(MATCHES_PATH).child(matchId).child("p1Username").get()
                    .addOnSuccessListener(nameSnap -> {
                        String opName = nameSnap.getValue(String.class);
                        matchCallback.onMatchFound(matchId, false, opName != null ? opName : "Opponent");
                    })
                    .addOnFailureListener(e -> matchCallback.onMatchFound(matchId, false, "Opponent"));
            }
            @Override public void onCancelled(DatabaseError e) {}
        };

        matchIdRef.addValueEventListener(listener);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (resolved[0]) return;
            rtdb.child(QUEUE_PATH).child(uid).get().addOnSuccessListener(mySnap -> {
                if (!mySnap.exists()) return;
                Object midVal = mySnap.child("matchId").getValue();
                if (midVal != null && !midVal.toString().isEmpty()) return; // already matched
                Object usernameObj = mySnap.child("username").getValue();
                String myUsername = usernameObj != null ? usernameObj.toString() : "Player";

                rtdb.child(QUEUE_PATH).orderByChild("matchId").equalTo("")
                    .get().addOnSuccessListener(queueSnap -> {
                        if (resolved[0]) return;
                        DataSnapshot opponent = null;
                        for (DataSnapshot child : queueSnap.getChildren()) {
                            if (!uid.equals(child.getKey())) { opponent = child; break; }
                        }
                        if (opponent != null) {
                            tryClaimOpponent(opponent, uid, myUsername, matchCallback,
                                new RepositoryCallback<Void>() {
                                    @Override public void onSuccess(Void v) {}
                                    @Override public void onFailure(Exception e) {}
                                });
                        }
                    });
            });
        }, RETRY_DELAY_MS);
    }

    public void leaveQueue(String uid) {
        if (uid == null) return;
        rtdb.child(QUEUE_PATH).child(uid).removeValue();
    }

    public void writeGameScore(String matchId, int gameIdx, int p1Score, int p2Score,
                                RepositoryCallback<Void> callback) {
        Map<String, Object> scores = new HashMap<>();
        scores.put("p1", p1Score);
        scores.put("p2", p2Score);
        scores.put("p1Done", true);
        scores.put("p2Done", true);
        rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child(String.valueOf(gameIdx))
            .setValue(scores)
            .addOnSuccessListener(v -> callback.onSuccess(null))
            .addOnFailureListener(callback::onFailure);
    }

    public void writePlayerScore(String matchId, int gameIdx, boolean isPlayer1,
                                  int score, RepositoryCallback<Void> callback) {
        String key = isPlayer1 ? "p1" : "p2";
        String doneKey = isPlayer1 ? "p1Done" : "p2Done";

        Map<String, Object> updates = new HashMap<>();
        updates.put(key, score);
        updates.put(doneKey, true);

        rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child(String.valueOf(gameIdx))
            .updateChildren(updates)
            .addOnSuccessListener(v -> callback.onSuccess(null))
            .addOnFailureListener(callback::onFailure);
    }

    public ValueEventListener listenForGameScore(String matchId, int gameIdx, ScoreCallback callback) {
        DatabaseReference ref = rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child(String.valueOf(gameIdx));
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Object p1v = snap.child("p1").getValue();
                Object p2v = snap.child("p2").getValue();
                if (p1v == null || p2v == null) return;
                int p1 = ((Number) p1v).intValue();
                int p2 = ((Number) p2v).intValue();

                Boolean p1Done = snap.child("p1Done").getValue(Boolean.class);
                Boolean p2Done = snap.child("p2Done").getValue(Boolean.class);

                callback.onScoreReady(p1, p2, p1Done && p2Done);

                if (p1Done != null && p2Done != null) {
                    if (p1Done && p2Done) ref.removeEventListener(this);
                } else { // fallback
                    if (p1 >= 0 && p2 >= 0) ref.removeEventListener(this);
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public ValueEventListener listenForP1Done(String matchId, int gameIdx, Runnable onReady) {
        DatabaseReference ref = rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child(String.valueOf(gameIdx)).child("p1Done");
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Object val = snap.getValue();
                if (Boolean.TRUE.equals(val)) {
                    ref.removeEventListener(this);
                    onReady.run();
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void removeP1DoneListener(String matchId, int gameIdx, ValueEventListener listener) {
        rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child(String.valueOf(gameIdx)).child("p1Done")
            .removeEventListener(listener);
    }

    public void removeScoreListener(String matchId, int gameIdx, ValueEventListener listener) {
        rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child(String.valueOf(gameIdx))
            .removeEventListener(listener);
    }

    public void finishMatch(String matchId, String myUid, int myScore, int opponentScore) {
        UserStatusManager.setInGame(auth, false);
        rtdb.child(MATCHES_PATH).child(matchId).child("status").setValue("finished");
        updateStars(myUid, myScore, opponentScore);
    }

    private void updateStars(String uid, int myScore, int opponentScore) {
        db.collection("profiles").document(uid).get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) return;
                Long current = doc.getLong("stars");
                long stars = current != null ? current : 0;

                long bonusStars = myScore / 40;
                long delta;
                if (myScore > opponentScore)      delta = 10 + bonusStars;
                else if (myScore < opponentScore) delta = -10 + bonusStars;
                else                              delta = bonusStars;
                long newStars = Math.max(0, stars + delta);

                long oldMilestones = stars / 50;
                long newMilestones = newStars / 50;
                long tokenBonus = newMilestones - oldMilestones;

                Map<String, Object> updates = new HashMap<>();
                updates.put("stars", newStars);
                if (tokenBonus > 0) {
                    updates.put("tokens", FieldValue.increment(tokenBonus));
                }
                db.collection("profiles").document(uid).update(updates);
            });
    }

    public void writeKpkP1StealQuestion(String matchId, String questionId,
                                         RepositoryCallback<Void> callback) {
        rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child("4").child("p1StealQ").setValue(questionId)
            .addOnSuccessListener(v -> callback.onSuccess(null))
            .addOnFailureListener(callback::onFailure);
    }

    public void readKpkP1StealQuestion(String matchId, RepositoryCallback<String> callback) {
        rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child("4").child("p1StealQ").get()
            .addOnSuccessListener(snap -> {
                Object v = snap.getValue();
                callback.onSuccess(v != null ? v.toString() : null);
            })
            .addOnFailureListener(callback::onFailure);
    }

    public void writeKpkP2StealQuestion(String matchId, String questionId,
                                         RepositoryCallback<Void> callback) {
        rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child("4").child("p2StealQ").setValue(questionId)
            .addOnSuccessListener(v -> callback.onSuccess(null))
            .addOnFailureListener(callback::onFailure);
    }

    public void readKpkP2StealQuestion(String matchId, RepositoryCallback<String> callback) {
        rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child("4").child("p2StealQ").get()
            .addOnSuccessListener(snap -> {
                Object v = snap.getValue();
                callback.onSuccess(v != null ? v.toString() : null);
            })
            .addOnFailureListener(callback::onFailure);
    }

    public void writeKpkFinal(String matchId, int finalP1, RepositoryCallback<Void> callback) {
        rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child("4").child("p1Final").setValue(finalP1)
            .addOnSuccessListener(v -> callback.onSuccess(null))
            .addOnFailureListener(callback::onFailure);
    }

    public ValueEventListener listenForKpkComplete(String matchId, Game4ResolvedCallback callback) {
        DatabaseReference ref = rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child("4");
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Object p1FinalV = snap.child("p1Final").getValue();
                Object p2V = snap.child("p2").getValue();
                if (p1FinalV == null || p2V == null) return;
                int fp1 = ((Number) p1FinalV).intValue();
                int p2 = ((Number) p2V).intValue();
                if (fp1 < 0 || p2 < 0) return;
                ref.removeEventListener(this);
                callback.onResolved(fp1, p2);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void removeKpkCompleteListener(String matchId, ValueEventListener listener) {
        rtdb.child(MATCHES_PATH).child(matchId)
            .child("scores").child("4").removeEventListener(listener);
    }

    public void writeMojBrojTarget(String matchId, int round, int target,
                                    RepositoryCallback<Void> callback) {
        rtdb.child(MATCHES_PATH).child(matchId).child("mojBroj")
            .child("round" + round).child("target").setValue(target)
            .addOnSuccessListener(v -> callback.onSuccess(null))
            .addOnFailureListener(callback::onFailure);
    }

    public void writeMojBrojNumbers(String matchId, int round, int[] numbers,
                                     RepositoryCallback<Void> callback) {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        for (int n : numbers) list.add(n);
        rtdb.child(MATCHES_PATH).child(matchId).child("mojBroj")
            .child("round" + round).child("numbers").setValue(list)
            .addOnSuccessListener(v -> callback.onSuccess(null))
            .addOnFailureListener(callback::onFailure);
    }

    public void writeMojBrojResult(String matchId, int round, boolean isPlayer1,
                                    Integer result, RepositoryCallback<Void> callback) {
        String key = isPlayer1 ? "p1Result" : "p2Result";
        Object value = result != null ? result : "null";
        rtdb.child(MATCHES_PATH).child(matchId).child("mojBroj")
            .child("round" + round).child(key).setValue(value)
            .addOnSuccessListener(v -> callback.onSuccess(null))
            .addOnFailureListener(callback::onFailure);
    }

    public ValueEventListener listenForMojBrojTarget(String matchId, int round,
                                                      MojBrojIntCallback callback) {
        DatabaseReference ref = rtdb.child(MATCHES_PATH).child(matchId)
            .child("mojBroj").child("round" + round).child("target");
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Object v = snap.getValue();
                if (!(v instanceof Number)) return;
                ref.removeEventListener(this);
                callback.onValue(((Number) v).intValue());
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void removeMojBrojTargetListener(String matchId, int round, ValueEventListener listener) {
        rtdb.child(MATCHES_PATH).child(matchId).child("mojBroj")
            .child("round" + round).child("target").removeEventListener(listener);
    }

    public ValueEventListener listenForMojBrojNumbers(String matchId, int round,
                                                       MojBrojNumbersCallback callback) {
        DatabaseReference ref = rtdb.child(MATCHES_PATH).child(matchId)
            .child("mojBroj").child("round" + round).child("numbers");
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Object v = snap.getValue();
                if (!(v instanceof java.util.List)) return;
                java.util.List<?> list = (java.util.List<?>) v;
                if (list.size() < 6) return;
                int[] arr = new int[6];
                for (int i = 0; i < 6; i++) {
                    Object item = list.get(i);
                    if (item instanceof Number) arr[i] = ((Number) item).intValue();
                    else return;
                }
                ref.removeEventListener(this);
                callback.onValue(arr);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void removeMojBrojNumbersListener(String matchId, int round, ValueEventListener listener) {
        rtdb.child(MATCHES_PATH).child(matchId).child("mojBroj")
            .child("round" + round).child("numbers").removeEventListener(listener);
    }

    public ValueEventListener listenForMojBrojResults(String matchId, int round,
                                                       MojBrojResultsCallback callback) {
        DatabaseReference ref = rtdb.child(MATCHES_PATH).child(matchId)
            .child("mojBroj").child("round" + round);
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Object p1V = snap.child("p1Result").getValue();
                Object p2V = snap.child("p2Result").getValue();
                if (p1V == null || p2V == null) return;
                Integer p1 = (p1V instanceof Number) ? ((Number) p1V).intValue() : null;
                Integer p2 = (p2V instanceof Number) ? ((Number) p2V).intValue() : null;
                ref.removeEventListener(this);
                callback.onValues(p1, p2);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void removeMojBrojResultsListener(String matchId, int round, ValueEventListener listener) {
        rtdb.child(MATCHES_PATH).child(matchId).child("mojBroj")
            .child("round" + round).removeEventListener(listener);
    }

    public void writeKzzOrCnnStarted(String matchId, String game) {
        rtdb.child(MATCHES_PATH).child(matchId).child("scores").child(game).child("data").child("started").setValue(true);

        Map<String, Object> updates = new HashMap<>();
        updates.put("p1", 0);
        updates.put("p2", 0);
        updates.put("p1Done", false);
        updates.put("p2Done", false);
        rtdb.child(MATCHES_PATH).child(matchId).child("scores").child(game).updateChildren(updates);
    }

    public ValueEventListener listenForKzzOrConnStarted(String matchId, String game, Runnable onStarted) {
        DatabaseReference ref = rtdb.child(MATCHES_PATH).child(matchId).child("scores").child(game).child("data").child("started");
        ValueEventListener listener = new ValueEventListener() {
            private boolean fired = false;
            @Override public void onDataChange(DataSnapshot snap) {
                if (fired) return;
                if (Boolean.TRUE.equals(snap.getValue(Boolean.class))) {
                    fired = true;
                    ref.removeEventListener(this);
                    onStarted.run();
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void writeForfeit(String matchId, String uid) {
        UserStatusManager.setInGame(auth, false);
        rtdb.child(MATCHES_PATH).child(matchId).child("forfeit").setValue(uid);
        rtdb.child(MATCHES_PATH).child(matchId).child("status").setValue("finished");
    }

    public void setupForfeitOnDisconnect(String matchId, String uid) {
        rtdb.child(MATCHES_PATH).child(matchId).child("forfeit").onDisconnect().setValue(uid);
    }

    public void cancelForfeitOnDisconnect(String matchId) {
        rtdb.child(MATCHES_PATH).child(matchId).child("forfeit").onDisconnect().cancel();
    }

    public ValueEventListener listenForForfeit(String matchId, String myUid, Runnable onOpponentForfeit) {
        DatabaseReference ref = rtdb.child(MATCHES_PATH).child(matchId).child("forfeit");
        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Object v = snap.getValue();
                if (v == null) return;
                if (!v.toString().equals(myUid)) {
                    ref.removeEventListener(this);
                    onOpponentForfeit.run();
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void removeForfeitListener(String matchId, ValueEventListener listener) {
        rtdb.child(MATCHES_PATH).child(matchId).child("forfeit").removeEventListener(listener);
    }

    public void deductToken(String uid, RepositoryCallback<Boolean> callback) {
        db.collection("profiles").document(uid).get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) { callback.onSuccess(false); return; }
                long now = System.currentTimeMillis();
                long oneDayMs = 24L * 60 * 60 * 1000;
                Long lastRefresh = doc.getLong("lastTokenRefresh");
                Long tokens = doc.getLong("tokens");
                int current = tokens != null ? tokens.intValue() : 0;
                boolean refresh = lastRefresh == null || now - lastRefresh > oneDayMs;
                int afterRefresh = refresh ? current + 5 : current;
                if (afterRefresh <= 0) { callback.onSuccess(false); return; }
                Map<String, Object> upd = new HashMap<>();
                upd.put("tokens", afterRefresh - 1);
                if (refresh) upd.put("lastTokenRefresh", now);
                db.collection("profiles").document(uid).update(upd)
                    .addOnSuccessListener(v -> callback.onSuccess(true))
                    .addOnFailureListener(callback::onFailure);
            })
            .addOnFailureListener(callback::onFailure);
    }
}
