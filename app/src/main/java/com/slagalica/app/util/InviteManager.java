package com.slagalica.app.util;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class InviteManager {

    private String shownInviteId;
    private static InviteManager instance;
    public static InviteManager get() {
        if (instance == null) instance = new InviteManager();
        return instance;
    }

    public String getShownInviteId() {
        return shownInviteId;
    }

    public void setShownInviteId(String shownInviteId) {
        this.shownInviteId = shownInviteId;
    }

    private static final FirebaseDatabase rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/");

    private final MutableLiveData<String[]> incomingInvite = new MutableLiveData<>();
    public LiveData<String[]> getIncomingInvite() { return incomingInvite; }

    private DatabaseReference ref;
    private ChildEventListener listener;

    public void startListening() {
        if (listener != null) return;

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) return;

        ref = rtdb.getReference("invites");
        listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String prev) {
                FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                if (current == null) return;

                String toUid   = snapshot.child("toUid").getValue(String.class);
                String fromUid = snapshot.child("fromUid").getValue(String.class);
                String status  = snapshot.child("status").getValue(String.class);
                Long createdAt = snapshot.child("createdAt").getValue(Long.class);
                long fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000;

                if ("pending".equals(status) && current.getUid().equals(toUid) && createdAt != null && createdAt > fiveMinutesAgo)
                    incomingInvite.postValue(new String[]{snapshot.getKey(), fromUid});
            }
            @Override public void onChildChanged(@NonNull DataSnapshot s, String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        ref.addChildEventListener(listener);
    }

    public void stopListening() {
        if (ref != null && listener != null) ref.removeEventListener(listener);
        listener = null;
        ref = null;
    }

    public void clearIncomingInvite() {
        shownInviteId = null;
        incomingInvite.postValue(null);
    }
}