package com.slagalica.app.repository;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.slagalica.app.model.Profile;
import com.slagalica.app.model.User;
import com.slagalica.app.util.UserStatusManager;

public class UserRepository {

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    private static final String USERS_COLLECTION = "users";

    public UserRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void register(String email, String username, String region, String password, RepositoryCallback<Void> callback) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener(authResult -> {
                FirebaseUser firebaseUser = authResult.getUser();
                String uid = firebaseUser.getUid();
                assignUserToRegion(region);

                User user = new User(uid, username, email, region);
                Profile profile = new Profile(uid, "", 5, 0, 0);

                db.collection(USERS_COLLECTION).document(uid).set(user)
                        .addOnSuccessListener(unused -> {
                            db.collection("profiles").document(uid).set(profile)
                                    .addOnSuccessListener(profileUnused -> {
                                        firebaseUser.sendEmailVerification()
                                                .addOnSuccessListener(v -> callback.onSuccess(null))
                                                .addOnFailureListener(callback::onFailure);
                                    })
                                    .addOnFailureListener(callback::onFailure);
                        })
                        .addOnFailureListener(callback::onFailure);
            })
            .addOnFailureListener(callback::onFailure);
    }

    public void loginWithEmail(String email, String password, RepositoryCallback<FirebaseUser> callback) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener(authResult -> {
                FirebaseUser firebaseUser = authResult.getUser();
                if (!firebaseUser.isEmailVerified()) {
                    auth.signOut();
                    callback.onFailure(new Exception("Email not verified. Check your inbox."));
                } else {
                    FirebaseFirestore.getInstance().collection("users")
                            .document(firebaseUser.getUid())
                            .get().addOnSuccessListener(document -> {
                                User user = document.toObject(User.class);
                                if (user != null)
                                    UserStatusManager.goOnline(FirebaseAuth.getInstance(), user.getRegion());
                            })
                            .addOnFailureListener(e -> UserStatusManager.goOnline(FirebaseAuth.getInstance(), ""));

                    callback.onSuccess(firebaseUser);
                }
            })
            .addOnFailureListener(callback::onFailure);
    }

    public void loginWithUsername(String username, String password, RepositoryCallback<FirebaseUser> callback) {
        db.collection(USERS_COLLECTION)
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .addOnSuccessListener(query -> {
                if (query.isEmpty()) {
                    callback.onFailure(new Exception("Username does not exist."));
                } else {
                    String email = query.getDocuments().get(0).getString("email");
                    loginWithEmail(email, password, callback);
                }
            })
            .addOnFailureListener(callback::onFailure);
    }

    public void changePassword(String oldPassword, String newPassword, RepositoryCallback<Void> callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onFailure(new Exception("User is not logged in."));
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(firebaseUser.getEmail(), oldPassword);

        firebaseUser.reauthenticate(credential)
            .addOnSuccessListener(unused ->
                firebaseUser.updatePassword(newPassword)
                    .addOnSuccessListener(v -> callback.onSuccess(null))
                    .addOnFailureListener(callback::onFailure))
            .addOnFailureListener(callback::onFailure);
    }

    public void loginAsGuest(RepositoryCallback<FirebaseUser> callback) {
        auth.signInAnonymously()
            .addOnSuccessListener(result -> callback.onSuccess(result.getUser()))
            .addOnFailureListener(callback::onFailure);
    }

    public void assignUserToRegion(String regionKey) {
        WriteBatch batch = db.batch();

        DocumentReference statsRef = db.collection("regions").document(regionKey);
        batch.update(statsRef, "totalPlayers", FieldValue.increment(1));

        batch.commit().addOnSuccessListener(aVoid -> {});
    }

    public void logout() { UserStatusManager.goOffline(auth); }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }
}
