package com.slagalica.app.repository;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.slagalica.app.model.ChatMessage;

import java.util.HashMap;
import java.util.Map;

public class ChatRepository {

    private static final String CHAT_PATH = "chat";

    private final DatabaseReference rtdb;
    private final FirebaseAuth auth;

    public interface MessageListener {
        void onMessageAdded(ChatMessage message);
    }

    public ChatRepository() {
        rtdb = FirebaseDatabase.getInstance(
            "https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/").getReference();
        auth = FirebaseAuth.getInstance();
    }

    public String getUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    public void sendMessage(String regionKey, String senderName, String text,
                            RepositoryCallback<Void> callback) {
        String uid = getUid();
        if (uid == null) { callback.onFailure(new Exception("Not logged in")); return; }

        Map<String, Object> msg = new HashMap<>();
        msg.put("senderUid", uid);
        msg.put("senderName", senderName);
        msg.put("text", text);
        msg.put("timestamp", System.currentTimeMillis());

        rtdb.child(CHAT_PATH).child(regionKey).push().setValue(msg)
            .addOnSuccessListener(v -> callback.onSuccess(null))
            .addOnFailureListener(callback::onFailure);
    }

    public ChildEventListener listenForMessages(String regionKey, MessageListener listener) {
        DatabaseReference ref = rtdb.child(CHAT_PATH).child(regionKey);
        ChildEventListener childListener = new ChildEventListener() {
            @Override public void onChildAdded(@NonNull DataSnapshot snapshot, String prev) {
                ChatMessage m = snapshot.getValue(ChatMessage.class);
                if (m != null) listener.onMessageAdded(m);
            }
            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        ref.addChildEventListener(childListener);
        return childListener;
    }

    public void removeListener(String regionKey, ChildEventListener listener) {
        if (listener == null) return;
        rtdb.child(CHAT_PATH).child(regionKey).removeEventListener(listener);
    }
}
