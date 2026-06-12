package com.slagalica.app.repository;

import android.util.Pair;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.model.Profile;
import com.slagalica.app.model.User;

public class ProfileRepository {

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private static final String PROFILES_COLLECTION = "profiles";

    public ProfileRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void getProfile(String uid, RepositoryCallback<Pair<User, Profile>> callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onFailure(new Exception("User is not logged in."));
            return;
        }

        String finalUid = uid == null ? firebaseUser.getUid() : uid;

        db.collection("users").document(finalUid).get()
                .addOnSuccessListener(userDoc -> {
                    db.collection(PROFILES_COLLECTION).document(finalUid).get()
                            .addOnSuccessListener(profileDoc ->
                            {
                                User user = userDoc.toObject(User.class);
                                Profile profile = profileDoc.toObject(Profile.class);
                                callback.onSuccess(new Pair<>(user, profile));
                            })
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    public void updateAvatar(String avatarUrl, RepositoryCallback<Void> callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onFailure(new Exception("User is not logged in."));
            return;
        }

        db.collection("profiles")
                .document(firebaseUser.getUid())
                .update("avatarUrl", avatarUrl)
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(callback::onFailure);
    }
}