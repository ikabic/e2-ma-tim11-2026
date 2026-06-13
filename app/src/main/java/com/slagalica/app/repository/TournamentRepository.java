package com.slagalica.app.repository;

import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import android.content.Context;


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

import java.util.HashMap;
import java.util.Map;

public class TournamentRepository {

    private static final String QUEUE_PATH  = "tournament_queue";
    private static final String TOURNAMENT_PATH = "tournaments";
    private static final int  RETRY_DELAY_MS = 4000;
    public static final int ENTRY_COST_TOKENS = 3;
    public static final int SEMIFINAL_WIN_TOKENS = 2;
    public static final int FINAL_WIN_TOKENS = 3;
    public static final int FINAL_WIN_BONUS_STARS = 10;
    private final DatabaseReference rtdb;
    private final FirebaseFirestore  db;
    private final FirebaseAuth auth;
    private ValueEventListener queueWatcher;

    public interface TournamentFoundCallback {
        void onTournamentFound(String tournamentId, String semifinal, boolean isPlayer1, String matchId, String opponentUsername, DataSnapshot allPlayers);
    }

    public interface SemifinalDoneCallback {
        void onBothSemifinalsFinished(String finalMatchId, String p1Uid, String p1Username, String p2Uid, String p2Username, boolean iAmInFinal, boolean isPlayer1InFinal);
    }

    public interface TournamentFinishedCallback {
        void onFinished(String winnerUid, String winnerUsername, String finalistUid, String finalistUsername, String semi1LoserUid, String semi2LoserUid);
    }

    public TournamentRepository() {
        rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public String getUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    public void joinQueue(String username, String avatarUrl, String league, TournamentFoundCallback foundCallback, RepositoryCallback<Void> errCallback) {
        String uid = getUid();
        if (uid == null) { errCallback.onFailure(new Exception("Not logged in")); return; }

        deductEntryTokens(uid, new RepositoryCallback<Boolean>() {
            @Override public void onSuccess(Boolean ok) {
                if (!Boolean.TRUE.equals(ok)) {
                    errCallback.onFailure(new Exception("Not enough tokens. You need 3 tokens to enter a tournament."));
                    return;
                }
                doJoinQueue(uid, username, avatarUrl, league, foundCallback, errCallback);
            }
            @Override public void onFailure(Exception e) { errCallback.onFailure(e); }
        });
        doJoinQueue(uid, username, avatarUrl, league, foundCallback, errCallback);
    }

    private void doJoinQueue(String uid, String username, String avatarUrl, String league, TournamentFoundCallback foundCallback, RepositoryCallback<Void> errCallback) {

        listenForTournament(uid, foundCallback);

        Map<String, Object> entry = new HashMap<>();
        entry.put("uid", uid);
        entry.put("username", username);
        entry.put("avatarUrl", avatarUrl != null ? avatarUrl : "");
        entry.put("league", league != null ? league : "Unranked");
        entry.put("timestamp", System.currentTimeMillis());
        entry.put("tournamentId", "");

        DatabaseReference myRef = rtdb.child(QUEUE_PATH).child(uid);
        myRef.onDisconnect().removeValue();

        myRef.setValue(entry)
                .addOnSuccessListener(v -> scanQueue(foundCallback))
                .addOnFailureListener(errCallback::onFailure);
    }

    private void scanQueue(TournamentFoundCallback foundCallback) {
        String myUid = getUid();
        if (myUid == null) return;

        DatabaseReference allQueueRef = rtdb.child(QUEUE_PATH);

        queueWatcher = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                List<DataSnapshot> candidates = new ArrayList<>();
                for (DataSnapshot child : snap.getChildren()) {
                    String key = child.getKey();
                    if (key == null) continue;
                    Object tid = child.child("tournamentId").getValue();
                    if (tid != null && !tid.toString().isEmpty()) continue;
                    if (child.child("uid").getValue() == null) continue;
                    candidates.add(child);
                }

                android.util.Log.d("TOURNAMENT", "queueWatcher candidates=" + candidates.size());

                if (candidates.size() < 4) return;

                candidates.sort((a, b) -> a.getKey().compareTo(b.getKey()));

                String leaderUid = candidates.get(0).getKey();
                android.util.Log.d("TOURNAMENT", "leader=" + leaderUid + " iAmLeader=" + myUid.equals(leaderUid));

                if (!myUid.equals(leaderUid)) return;

                allQueueRef.removeEventListener(this);
                queueWatcher = null;

                tryFormTournament(candidates.subList(0, 4), foundCallback);
            }

            @Override
            public void onCancelled(DatabaseError e) {
                android.util.Log.e("TOURNAMENT", "queueWatcher cancelled: " + e.getMessage());
            }
        };

        allQueueRef.addValueEventListener(queueWatcher);
    }

    private void electLeaderAndForm(String myUid, TournamentFoundCallback foundCallback) {
        electLeaderAndForm(myUid, foundCallback, 0);
    }

    private void electLeaderAndForm(String myUid, TournamentFoundCallback foundCallback, int attempt) {
        if (attempt > 10) return;

        rtdb.child(QUEUE_PATH)
                .orderByChild("tournamentId")
                .equalTo("")
                .get()
                .addOnSuccessListener(snap -> {
                    List<DataSnapshot> candidates = new ArrayList<>();
                    for (DataSnapshot child : snap.getChildren()) {
                        String key = child.getKey();
                        if (key == null) continue;
                        Object tid = child.child("tournamentId").getValue();
                        if (tid != null && !tid.toString().isEmpty()) continue;
                        candidates.add(child);
                    }

                    android.util.Log.d("TOURNAMENT", "electLeader attempt=" + attempt + " candidates=" + candidates.size());

                    if (candidates.size() < 4) {
                        new Handler(Looper.getMainLooper()).postDelayed(
                                () -> electLeaderAndForm(myUid, foundCallback, attempt + 1), 1000);
                        return;
                    }

                    candidates.sort((a, b) -> a.getKey().compareTo(b.getKey()));

                    DataSnapshot leader = candidates.get(0);
                    android.util.Log.d("TOURNAMENT", "leader=" + leader.getKey() + " iAmLeader=" + myUid.equals(leader.getKey()));

                    if (!myUid.equals(leader.getKey())) return;
                    android.util.Log.d("TOURNAMENT", "CALLING tryFormTournament, iAmLeader=true");
                    tryFormTournament(candidates.subList(0, 4), foundCallback);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("TOURNAMENT", "electLeader failed: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).postDelayed(
                            () -> electLeaderAndForm(myUid, foundCallback, attempt + 1), 1000);
                });
    }

    private void tryFormTournament(List<DataSnapshot> players, TournamentFoundCallback foundCallback) {

        String tournamentId = rtdb.child(TOURNAMENT_PATH).push().getKey();
        if (tournamentId == null) return;

        String[] uids = new String[4];
        String[] usernames = new String[4];
        String[] avatars = new String[4];
        String[] leagues = new String[4];

        for (int i = 0; i < 4; i++) {
            DataSnapshot p = players.get(i);

            uids[i] = p.getKey();
            usernames[i] = p.child("username").getValue(String.class);
            avatars[i] = p.child("avatarUrl").getValue(String.class);
            leagues[i] = p.child("league").getValue(String.class);

            if (usernames[i] == null) usernames[i] = "Player";
            if (avatars[i] == null) avatars[i] = "";
            if (leagues[i] == null) leagues[i] = "Unranked";
        }

        createTournament(tournamentId, uids, usernames, avatars, leagues, foundCallback);
    }

    private void createTournament(String tournamentId, String[] uids, String[] usernames, String[] avatars, String[] leagues, TournamentFoundCallback foundCallback) {

        String matchIdA = rtdb.child("matches").push().getKey();
        String matchIdB = rtdb.child("matches").push().getKey();

        Map<String, Object> tournament = new HashMap<>();
        tournament.put("status", "semifinal");
        tournament.put("createdAt", System.currentTimeMillis());

        Map<String, Object> players = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> p = new HashMap<>();
            p.put("username", usernames[i]);
            p.put("avatarUrl", avatars[i]);
            p.put("league", leagues[i]);
            p.put("semifinal", i < 2 ? "A" : "B");
            p.put("isWinner", false);
            players.put(uids[i], p);
        }

        tournament.put("players", players);

        Map<String, Object> sfA = new HashMap<>();
        sfA.put("matchId", matchIdA);
        sfA.put("p1Uid", uids[0]);
        sfA.put("p2Uid", uids[1]);
        sfA.put("p1Username", usernames[0]);
        sfA.put("p2Username", usernames[1]);

        Map<String, Object> sfB = new HashMap<>();
        sfB.put("matchId", matchIdB);
        sfB.put("p1Uid", uids[2]);
        sfB.put("p2Uid", uids[3]);
        sfB.put("p1Username", usernames[2]);
        sfB.put("p2Username", usernames[3]);

        tournament.put("semifinalA", sfA);
        tournament.put("semifinalB", sfB);
        tournament.put("final", new HashMap<>());

        rtdb.child(TOURNAMENT_PATH).child(tournamentId)
                .setValue(tournament)
                .addOnSuccessListener(v -> {
                    AtomicInteger done = new AtomicInteger(0);
                    Runnable onBothMatchesWritten = () -> {
                        if (done.incrementAndGet() < 2) return;
                        android.util.Log.d("TOURNAMENT", "onBothMatchesWritten fired, writing tournamentId=" + tournamentId + " for uids[1..3]");
                        for (int i = 1; i < uids.length; i++) {
                            rtdb.child(QUEUE_PATH).child(uids[i]).child("tournamentId").setValue(tournamentId);
                        }

                        rtdb.child(QUEUE_PATH).child("__forming__").removeValue();

                        rtdb.child(QUEUE_PATH).child(uids[0]).removeValue();

                        rtdb.child(TOURNAMENT_PATH).child(tournamentId).child("players").get()
                                .addOnSuccessListener(playersSnap ->
                                        foundCallback.onTournamentFound(
                                                tournamentId, "A", true, matchIdA, usernames[1], playersSnap
                                        )
                                );
                    };

                    createMatchNode(matchIdA, uids[0], usernames[0], uids[1], usernames[1], onBothMatchesWritten);
                    createMatchNode(matchIdB, uids[2], usernames[2], uids[3], usernames[3], onBothMatchesWritten);
                });
    }

    private void createMatchNode(String matchId, String p1Uid, String p1Username, String p2Uid, String p2Username, Runnable onComplete) {
        Map<String, Object> match = new HashMap<>();
        match.put("p1Uid", p1Uid);
        match.put("p1Username", p1Username);
        match.put("p2Uid", p2Uid);
        match.put("p2Username", p2Username);
        match.put("status", "active");

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

        rtdb.child("matches").child(matchId).setValue(match)
                .addOnSuccessListener(v -> { if (onComplete != null) onComplete.run(); })
                .addOnFailureListener(e -> { if (onComplete != null) onComplete.run(); });
    }

    private void createMatchNode(String matchId, String p1Uid, String p1Username, String p2Uid, String p2Username) {
        Map<String, Object> match = new HashMap<>();
        match.put("p1Uid", p1Uid);
        match.put("p1Username", p1Username);
        match.put("p2Uid", p2Uid);
        match.put("p2Username", p2Username);
        match.put("status","active");

        Map<String, Object> scores = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            Map<String, Object> gs = new HashMap<>();
            gs.put("p1",-999);
            gs.put("p2",-999);
            gs.put("p1Done", false);
            gs.put("p2Done", false);
            scores.put(String.valueOf(i), gs);
        }
        match.put("scores", scores);
        rtdb.child("matches").child(matchId).setValue(match);
    }

    private void listenForTournament(String uid, TournamentFoundCallback foundCallback) {
        DatabaseReference ref = rtdb.child(QUEUE_PATH).child(uid).child("tournamentId");
        final boolean[] resolved = {false};

        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                android.util.Log.d("TOURNAMENT", "listenForTournament fired for uid=" + uid + " val=" + snap.getValue());
                Object val = snap.getValue();
                if (val == null || val.toString().isEmpty()) return;
                resolved[0] = true;
                String tournamentId = val.toString();
                ref.removeEventListener(this);

                rtdb.child(TOURNAMENT_PATH).child(tournamentId).get()
                        .addOnSuccessListener(tSnap -> {
                            if (!tSnap.exists()) return;

                            DataSnapshot playersSnap = tSnap.child("players");
                            DataSnapshot myData = playersSnap.child(uid);
                            String mySemifinal = myData.child("semifinal").getValue(String.class);
                            String sfKey = "semifinal" + mySemifinal;

                            DataSnapshot sf = tSnap.child(sfKey);
                            String matchId = sf.child("matchId").getValue(String.class);
                            String p1Uid = sf.child("p1Uid").getValue(String.class);
                            String p2Uid = sf.child("p2Uid").getValue(String.class);
                            boolean isP1 = uid.equals(p1Uid);
                            String opponentUid = isP1 ? p2Uid : p1Uid;

                            DataSnapshot opponentData = playersSnap.child(opponentUid != null ? opponentUid : "");
                            String opponentUsername = "";
                            Object uo = opponentData.child("username").getValue();
                            if (uo != null) opponentUsername = uo.toString();

                            foundCallback.onTournamentFound(tournamentId, mySemifinal, isP1, matchId, opponentUsername, playersSnap);
                        });
            }
            @Override public void onCancelled(DatabaseError e) {android.util.Log.e("TOURNAMENT", "listenForTournament cancelled: " + e.getMessage());}
        });
    }

    public void leaveQueue(String uid) {
        if (uid == null) return;
        if (queueWatcher != null) {
            rtdb.child(QUEUE_PATH).removeEventListener(queueWatcher);
            queueWatcher = null;
        }
        rtdb.child(QUEUE_PATH).child(uid).removeValue();
    }

    public void reportSemifinalResult(Context context, String tournamentId, String semifinal, String winnerUid, String winnerUsername, String loserUid, int winnerScore, int loserScore, SemifinalDoneCallback callback) {

        String sfKey = "semifinal" + semifinal;
        String myUid = getUid();

        rtdb.child(TOURNAMENT_PATH).child(tournamentId).child(sfKey).child("winnerUid").setValue(winnerUid);
        rtdb.child(TOURNAMENT_PATH).child(tournamentId).child("players").child(winnerUid).child("isWinner").setValue(true);
        awardSemifinalWin(context, winnerUid, winnerUsername, loserUid, winnerScore, loserScore);

        if (!myUid.equals(winnerUid)) {
            callback.onBothSemifinalsFinished(null, null, null, null, null, false, false);
            return;
        }

        DatabaseReference tournRef = rtdb.child(TOURNAMENT_PATH).child(tournamentId);
        tournRef.addValueEventListener(new ValueEventListener() {
            private boolean fired = false;

            @Override
            public void onDataChange(DataSnapshot snap) {
                if (fired) return;

                String sfAWinner = snap.child("semifinalA").child("winnerUid").getValue(String.class);
                String sfBWinner = snap.child("semifinalB").child("winnerUid").getValue(String.class);

                android.util.Log.d("FINAL", "reportSemifinal listener sfAWinner=" + sfAWinner + " sfBWinner=" + sfBWinner);

                boolean aReady = sfAWinner != null && !sfAWinner.isEmpty();
                boolean bReady = sfBWinner != null && !sfBWinner.isEmpty();

                if (!aReady || !bReady) return;

                fired = true;
                tournRef.removeEventListener(this);

                String finalP1Uid = sfAWinner;
                String finalP2Uid = sfBWinner;

                String fp1Username = getWinnerUsername(snap.child("semifinalA"), finalP1Uid);
                String fp2Username = getWinnerUsername(snap.child("semifinalB"), finalP2Uid);

                String finalMatchId = rtdb.child("matches").push().getKey();
                createMatchNode(finalMatchId, finalP1Uid, fp1Username, finalP2Uid, fp2Username);

                Map<String, Object> finalData = new HashMap<>();
                finalData.put("matchId", finalMatchId);
                finalData.put("p1Uid", finalP1Uid);
                finalData.put("p1Username", fp1Username);
                finalData.put("p2Uid", finalP2Uid);
                finalData.put("p2Username", fp2Username);
                finalData.put("winnerUid", "");

                rtdb.child(TOURNAMENT_PATH).child(tournamentId).child("final").setValue(finalData);
                rtdb.child(TOURNAMENT_PATH).child(tournamentId).child("status").setValue("final");

                boolean iAmInFinal = myUid.equals(finalP1Uid) || myUid.equals(finalP2Uid);
                boolean isP1InFinal = myUid.equals(finalP1Uid);

                android.util.Log.d("FINAL", "CALLING callback from listener iAmInFinal=" + iAmInFinal);
                callback.onBothSemifinalsFinished(finalMatchId, finalP1Uid, fp1Username, finalP2Uid, fp2Username, iAmInFinal, isP1InFinal);
            }

            @Override
            public void onCancelled(DatabaseError e) {
                android.util.Log.e("FINAL", "reportSemifinal listener cancelled: " + e.getMessage());
            }
        });
    }

    public ValueEventListener listenForFinalReady(String tournamentId, SemifinalDoneCallback callback) {
        DatabaseReference ref = rtdb.child(TOURNAMENT_PATH).child(tournamentId).child("final").child("matchId");
        ValueEventListener listener = new ValueEventListener() {
            private boolean fired = false;
            @Override public void onDataChange(DataSnapshot snap) {
                if (fired) return;
                Object val = snap.getValue();
                if (val == null || val.toString().isEmpty()) return;
                fired = true;
                ref.removeEventListener(this);

                String finalMatchId = val.toString();
                rtdb.child(TOURNAMENT_PATH).child(tournamentId).child("final").get()
                        .addOnSuccessListener(finalSnap -> {
                            String p1Uid = finalSnap.child("p1Uid").getValue(String.class);
                            String p2Uid = finalSnap.child("p2Uid").getValue(String.class);
                            String p1Un = finalSnap.child("p1Username").getValue(String.class);
                            String p2Un = finalSnap.child("p2Username").getValue(String.class);
                            String myUid = getUid();
                            boolean iAmInFinal = myUid != null && (myUid.equals(p1Uid) || myUid.equals(p2Uid));
                            boolean isP1 = myUid != null && myUid.equals(p1Uid);
                            android.util.Log.d("FINAL", "listenForFinalReady snap p1Uid=" + p1Uid
                                    + " p2Uid=" + p2Uid + " myUid=" + myUid
                                    + " iAmInFinal=" + iAmInFinal);
                            callback.onBothSemifinalsFinished(finalMatchId, p1Uid, p1Un, p2Uid, p2Un, iAmInFinal, isP1);
                        });
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void removeListener(DatabaseReference ref, ValueEventListener l) {
        if (l == null || ref == null) return;
        ref.removeEventListener(l);
    }

    public void reportFinalResult(android.content.Context context, String tournamentId, String winnerUid, String winnerUsername, String loserUid, String loserUsername, int winnerScore, int loserScore, TournamentFinishedCallback callback) {
        rtdb.child(TOURNAMENT_PATH).child(tournamentId).child("final").child("winnerUid").setValue(winnerUid);
        rtdb.child(TOURNAMENT_PATH).child(tournamentId).child("status").setValue("finished");

        awardFinalWin(context, winnerUid, winnerUsername, loserUid, loserUsername, winnerScore, loserScore);
        awardFinalLoss(context, loserUid, loserUsername, winnerUid, winnerUsername, loserScore, winnerScore);

        rtdb.child(TOURNAMENT_PATH).child(tournamentId).get()
                .addOnSuccessListener(snap -> {
                    String sfAWinner = snap.child("semifinalA").child("winnerUid").getValue(String.class);
                    String sfBWinner = snap.child("semifinalB").child("winnerUid").getValue(String.class);

                    String sfAP1 = snap.child("semifinalA").child("p1Uid").getValue(String.class);
                    String sfAP2 = snap.child("semifinalA").child("p2Uid").getValue(String.class);
                    String sfBP1 = snap.child("semifinalB").child("p1Uid").getValue(String.class);
                    String sfBP2 = snap.child("semifinalB").child("p2Uid").getValue(String.class);

                    String sfALoser = sfAP1 != null && !sfAP1.equals(sfAWinner) ? sfAP1 : sfAP2;
                    String sfBLoser = sfBP1 != null && !sfBP1.equals(sfBWinner) ? sfBP1 : sfBP2;

                    callback.onFinished(winnerUid, winnerUsername, loserUid, loserUsername, sfALoser, sfBLoser);
                });
    }

    private void awardSemifinalWin(android.content.Context context, String winnerUid, String winnerUsername, String loserUid, int myScore, int opponentScore) {
        db.collection("profiles").document(winnerUid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    long stars = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                    long bonus = myScore / 40;
                    long delta = 10 + bonus;
                    long newStars = Math.max(0, stars + delta);

                    String oldLeague = leagueForStars(stars);
                    String newLeague = leagueForStars(newStars);

                    Map<String, Object> upd = new HashMap<>();
                    upd.put("stars",  newStars);
                    upd.put("tokens", FieldValue.increment(SEMIFINAL_WIN_TOKENS));

                    db.collection("profiles").document(winnerUid).update(upd)
                            .addOnSuccessListener(v -> {
                                RankingRepository rankRepo = new RankingRepository();
                                int sd = (int) delta;
                                rankRepo.addStarsToCycle("weekly",  sd, new RepositoryCallback<Void>() {
                                    @Override public void onSuccess(Void r) {}
                                    @Override public void onFailure(Exception e) {}
                                });
                                rankRepo.addStarsToCycle("monthly", sd, new RepositoryCallback<Void>() {
                                    @Override public void onSuccess(Void r) {}
                                    @Override public void onFailure(Exception e) {}
                                });

                                if (context != null) {
                                    NotificationRepository nr = new NotificationRepository(context);
                                    if (!oldLeague.equals(newLeague)) {
                                        nr.createLeagueChangeNotif(winnerUid, newLeague);
                                    }
                                    nr.createMatchResultNotif(winnerUid, "tournament opponent",
                                            myScore, opponentScore);
                                }
                            });
                });
    }

    private void awardFinalWin(android.content.Context context, String winnerUid, String winnerUsername, String loserUid, String loserUsername, int myScore, int opponentScore) {
        db.collection("profiles").document(winnerUid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    long stars = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                    long bonus = myScore / 40;
                    long delta = 10 + bonus + FINAL_WIN_BONUS_STARS;
                    long newStars = Math.max(0, stars + delta);

                    Map<String, Object> upd = new HashMap<>();
                    upd.put("stars",  newStars);
                    upd.put("tokens", FieldValue.increment(FINAL_WIN_TOKENS));

                    db.collection("profiles").document(winnerUid).update(upd)
                            .addOnSuccessListener(v -> {
                                RankingRepository rankRepo = new RankingRepository();
                                int sd = (int) delta;
                                rankRepo.addStarsToCycle("weekly",  sd, new RepositoryCallback<Void>() {
                                    @Override public void onSuccess(Void r) {}
                                    @Override public void onFailure(Exception e) {}
                                });
                                rankRepo.addStarsToCycle("monthly", sd, new RepositoryCallback<Void>() {
                                    @Override public void onSuccess(Void r) {}
                                    @Override public void onFailure(Exception e) {}
                                });
                            });
                });
    }

    private void awardFinalLoss(android.content.Context context, String loserUid, String loserUsername, String winnerUid, String winnerUsername, int myScore, int opponentScore) {
        db.collection("profiles").document(loserUid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    long stars = doc.getLong("stars") != null ? doc.getLong("stars") : 0;
                    long bonus = myScore / 40;
                    long delta = -10 + bonus;
                    long newStars = Math.max(0, stars + delta);

                    Map<String, Object> upd = new HashMap<>();
                    upd.put("stars", newStars);
                    db.collection("profiles").document(loserUid).update(upd);

                    if (delta > 0) {
                        RankingRepository rankRepo = new RankingRepository();
                        int sd = (int) delta;
                        rankRepo.addStarsToCycle("weekly",  sd, new RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void r) {}
                            @Override public void onFailure(Exception e) {}
                        });
                        rankRepo.addStarsToCycle("monthly", sd, new RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void r) {}
                            @Override public void onFailure(Exception e) {}
                        });
                    }
                });
    }

    private void deductEntryTokens(String uid, RepositoryCallback<Boolean> callback) {
        db.collection("profiles").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onSuccess(false); return; }
                    Long tokens = doc.getLong("tokens");
                    int current = tokens != null ? tokens.intValue() : 0;
                    if (current < ENTRY_COST_TOKENS) { callback.onSuccess(false); return; }

                    Map<String, Object> upd = new HashMap<>();
                    upd.put("tokens", FieldValue.increment(-ENTRY_COST_TOKENS));
                    db.collection("profiles").document(uid).update(upd).addOnSuccessListener(v -> callback.onSuccess(true))
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void writeForfeit(String tournamentId, String uid) {
        if (tournamentId != null) {
            rtdb.child(TOURNAMENT_PATH).child(tournamentId).child("forfeit").setValue(uid);
        }
    }

    private String getWinnerUsername(DataSnapshot sf, String winnerUid) {
        String p1Uid = sf.child("p1Uid").getValue(String.class);
        if (winnerUid != null && winnerUid.equals(p1Uid)) {
            Object u = sf.child("p1Username").getValue();
            return u != null ? u.toString() : "";
        }
        Object u = sf.child("p2Username").getValue();
        return u != null ? u.toString() : "";
    }

    private String leagueForStars(long stars) {
        if (stars < 50)   return "Unranked";
        if (stars < 200)  return "Bronze";
        if (stars < 500)  return "Silver";
        if (stars < 1000) return "Gold";
        if (stars < 2000) return "Platinum";
        return "Diamond";
    }

    public DatabaseReference getFinalMatchIdRef(String tournamentId) {
        return rtdb.child(TOURNAMENT_PATH).child(tournamentId).child("final").child("matchId");
    }
}