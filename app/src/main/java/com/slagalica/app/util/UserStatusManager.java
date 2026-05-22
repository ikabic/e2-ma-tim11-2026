package com.slagalica.app.util;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class UserStatusManager {

    private static final FirebaseDatabase rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/");

    public static void goOnline(FirebaseAuth auth) {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) return;

        DatabaseReference ref = rtdb.getReference("presence/" + me.getUid());

        Map<String, Object> onDisconnectValues = new HashMap<>();
        onDisconnectValues.put("status", "offline");
        onDisconnectValues.put("inGame", false);
        ref.onDisconnect().updateChildren(onDisconnectValues);

        Map<String, Object> values = new HashMap<>();
        values.put("status", "online");
        values.put("inGame", false);
        ref.updateChildren(values);
    }

    public static void goOffline(FirebaseAuth auth) {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) return;

        DatabaseReference ref = rtdb.getReference("presence/" + me.getUid());

        Map<String, Object> values = new HashMap<>();
        values.put("status", "offline");
        values.put("inGame", false);
        ref.updateChildren(values);

        InviteManager.get().stopListening();
        auth.signOut();
    }

    public static void setInGame(FirebaseAuth auth, boolean inGame) {
        FirebaseUser me = auth.getCurrentUser();
        if (me == null) return;

        DatabaseReference ref = rtdb.getReference("presence/" + me.getUid());

        Map<String, Object> values = new HashMap<>();
        values.put("status", inGame ? "in_game" : "online");
        values.put("inGame", inGame);
        ref.updateChildren(values);
    }
}