package com.slagalica.app.repository;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.Challenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChallengeRepository {

    private static final String CHALLENGES = "challenges";
    private static final int MAX_PLAYERS = 4;

    private final DatabaseReference rtdb;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public ChallengeRepository() {
        rtdb = FirebaseDatabase.getInstance(
            "https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public String getUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    public static class Participant {
        public String uid;
        public String username;
        public int score;
        public boolean played;
    }

    public static class ChallengeState {
        public Challenge challenge;
        public List<Participant> participants;
        public String winnerUid;
        public String secondUid;
    }

    public interface ChallengeListCallback { void onChallenges(List<Challenge> challenges); }
    public interface ChallengeStateCallback { void onState(ChallengeState state); }

    public void createChallenge(String region, String creatorUsername, int stars, int tokens,
                                RepositoryCallback<String> callback) {
        String uid = getUid();
        if (uid == null) { callback.onFailure(new Exception("Not logged in")); return; }

        escrow(uid, stars, tokens, new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void v) {
                String challengeId = rtdb.child(CHALLENGES).push().getKey();

                Map<String, Object> participant = new HashMap<>();
                participant.put("username", creatorUsername);
                participant.put("score", -1);
                participant.put("played", false);
                Map<String, Object> participants = new HashMap<>();
                participants.put(uid, participant);

                Map<String, Object> ch = new HashMap<>();
                ch.put("creatorUid", uid);
                ch.put("creatorUsername", creatorUsername);
                ch.put("region", region);
                ch.put("starsStake", stars);
                ch.put("tokensStake", tokens);
                ch.put("status", "open");
                ch.put("createdAt", System.currentTimeMillis());
                ch.put("participants", participants);

                rtdb.child(CHALLENGES).child(challengeId).setValue(ch)
                    .addOnSuccessListener(x -> callback.onSuccess(challengeId))
                    .addOnFailureListener(callback::onFailure);
            }
            @Override public void onFailure(Exception e) { callback.onFailure(e); }
        });
    }

    public void joinChallenge(String challengeId, String username, RepositoryCallback<Void> callback) {
        String uid = getUid();
        if (uid == null) { callback.onFailure(new Exception("Not logged in")); return; }

        rtdb.child(CHALLENGES).child(challengeId).get().addOnSuccessListener(snap -> {
            if (!snap.exists()) { callback.onFailure(new Exception("Challenge not found")); return; }
            if (!"open".equals(snap.child("status").getValue(String.class))) {
                callback.onFailure(new Exception("Challenge is no longer open")); return;
            }
            if (snap.child("participants").child(uid).exists()) {
                callback.onFailure(new Exception("Already joined")); return;
            }
            if (snap.child("participants").getChildrenCount() >= MAX_PLAYERS) {
                callback.onFailure(new Exception("Challenge is full")); return;
            }
            int stars = intOf(snap.child("starsStake"));
            int tokens = intOf(snap.child("tokensStake"));

            escrow(uid, stars, tokens, new RepositoryCallback<Void>() {
                @Override public void onSuccess(Void v) {
                    Map<String, Object> participant = new HashMap<>();
                    participant.put("username", username);
                    participant.put("score", -1);
                    participant.put("played", false);
                    rtdb.child(CHALLENGES).child(challengeId).child("participants").child(uid)
                        .setValue(participant)
                        .addOnSuccessListener(x -> callback.onSuccess(null))
                        .addOnFailureListener(callback::onFailure);
                }
                @Override public void onFailure(Exception e) { callback.onFailure(e); }
            });
        }).addOnFailureListener(callback::onFailure);
    }

    public ValueEventListener listenForOpenChallenges(String region, ChallengeListCallback callback) {
        Query ref = rtdb.child(CHALLENGES).orderByChild("region").equalTo(region);
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                List<Challenge> list = new ArrayList<>();
                for (DataSnapshot c : snap.getChildren()) {
                    if (!"open".equals(c.child("status").getValue(String.class))) continue;
                    Challenge ch = c.getValue(Challenge.class);
                    if (ch != null) {
                        ch.setId(c.getKey());
                        ch.setPlayerCount((int) c.child("participants").getChildrenCount());
                        list.add(ch);
                    }
                }
                callback.onChallenges(list);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void removeOpenChallengesListener(String region, ValueEventListener listener) {
        if (listener == null) return;
        rtdb.child(CHALLENGES).orderByChild("region").equalTo(region).removeEventListener(listener);
    }

    public ValueEventListener listenForChallenge(String challengeId, ChallengeStateCallback callback) {
        DatabaseReference ref = rtdb.child(CHALLENGES).child(challengeId);
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snap) {
                if (!snap.exists()) return;
                ChallengeState state = new ChallengeState();
                state.challenge = snap.getValue(Challenge.class);
                if (state.challenge != null) state.challenge.setId(snap.getKey());
                state.participants = parseParticipants(snap);
                state.winnerUid = snap.child("results").child("winnerUid").getValue(String.class);
                state.secondUid = snap.child("results").child("secondUid").getValue(String.class);
                callback.onState(state);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
        return listener;
    }

    public void removeChallengeListener(String challengeId, ValueEventListener listener) {
        if (listener == null) return;
        rtdb.child(CHALLENGES).child(challengeId).removeEventListener(listener);
    }

    public void setChallengePlaying(String challengeId) {
        rtdb.child(CHALLENGES).child(challengeId).child("status").setValue("playing");
    }

    public void submitScore(String challengeId, int score, RepositoryCallback<Void> callback) {
        String uid = getUid();
        if (uid == null) { callback.onFailure(new Exception("Not logged in")); return; }
        Map<String, Object> upd = new HashMap<>();
        upd.put("score", score);
        upd.put("played", true);
        rtdb.child(CHALLENGES).child(challengeId).child("participants").child(uid)
            .updateChildren(upd)
            .addOnSuccessListener(v -> callback.onSuccess(null))
            .addOnFailureListener(callback::onFailure);
    }

    public void resolveAndPayout(String challengeId, RepositoryCallback<Void> callback) {
        DatabaseReference ref = rtdb.child(CHALLENGES).child(challengeId);
        ref.get().addOnSuccessListener(snap -> {
            if (!snap.exists()) { callback.onFailure(new Exception("Challenge not found")); return; }

            List<Participant> ps = parseParticipants(snap);
            for (Participant p : ps) {
                if (!p.played) { callback.onSuccess(null); return; }
            }
            int stars = intOf(snap.child("starsStake"));
            int tokens = intOf(snap.child("tokensStake"));
            int n = ps.size();
            int potStars = n * stars;
            int potTokens = n * tokens;

            Collections.sort(ps, (a, b) -> b.score - a.score);

            ref.child("status").runTransaction(new Transaction.Handler() {
                @Override public Transaction.Result doTransaction(@NonNull MutableData data) {
                    Object v = data.getValue();
                    if ("finished".equals(v)) return Transaction.abort();
                    data.setValue("finished");
                    return Transaction.success(data);
                }
                @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot current) {
                    if (!committed) { callback.onSuccess(null); return; }

                    Participant winner = ps.get(0);
                    award(winner.uid, (int) (potStars * 0.75), (int) (potTokens * 0.75));

                    Map<String, Object> results = new HashMap<>();
                    results.put("winnerUid", winner.uid);
                    if (ps.size() >= 2) {
                        Participant second = ps.get(1);
                        award(second.uid, stars, tokens);
                        results.put("secondUid", second.uid);
                    }
                    ref.child("results").setValue(results);
                    callback.onSuccess(null);
                }
            });
        }).addOnFailureListener(callback::onFailure);
    }

    private void escrow(String uid, int stars, int tokens, RepositoryCallback<Void> callback) {
        db.collection("profiles").document(uid).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) { callback.onFailure(new Exception("Profile not found")); return; }
            Long sL = doc.getLong("stars");
            Long tL = doc.getLong("tokens");
            long curStars = sL != null ? sL : 0;
            long curTokens = tL != null ? tL : 0;
            if (curStars < stars || curTokens < tokens) {
                callback.onFailure(new Exception("Not enough stars or tokens")); return;
            }
            Map<String, Object> upd = new HashMap<>();
            upd.put("stars", curStars - stars);
            upd.put("tokens", curTokens - tokens);
            db.collection("profiles").document(uid).update(upd)
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
        }).addOnFailureListener(callback::onFailure);
    }

    private void award(String uid, int stars, int tokens) {
        Map<String, Object> upd = new HashMap<>();
        if (stars != 0) upd.put("stars", FieldValue.increment(stars));
        if (tokens != 0) upd.put("tokens", FieldValue.increment(tokens));
        if (!upd.isEmpty()) db.collection("profiles").document(uid).update(upd);
    }

    private List<Participant> parseParticipants(DataSnapshot snap) {
        List<Participant> ps = new ArrayList<>();
        for (DataSnapshot p : snap.child("participants").getChildren()) {
            Participant part = new Participant();
            part.uid = p.getKey();
            part.username = p.child("username").getValue(String.class);
            part.score = intOf(p.child("score"));
            part.played = Boolean.TRUE.equals(p.child("played").getValue(Boolean.class));
            ps.add(part);
        }
        return ps;
    }

    private int intOf(DataSnapshot snap) {
        Object v = snap.getValue();
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }
}
