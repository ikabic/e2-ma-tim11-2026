package com.slagalica.app;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class FCMTokenManager {
    public static void refreshAndSave() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> saveTokenForUser(user.getUid(), token));
    }

    public static void saveToken(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        saveTokenForUser(user.getUid(), token);
    }

    private static void saveTokenForUser(String uid, String token) {
        Map<String, Object> update = new HashMap<>();
        update.put("fcmToken", token);
        FirebaseFirestore.getInstance()
                .collection("profiles")
                .document(uid)
                .update(update);
    }
}