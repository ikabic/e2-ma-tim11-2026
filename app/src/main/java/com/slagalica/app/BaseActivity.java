package com.slagalica.app;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.repository.NotificationRepository;
import com.slagalica.app.repository.RepositoryCallback;
import com.slagalica.app.util.InviteManager;
import com.slagalica.app.util.IncomingInviteDialog;
import com.slagalica.app.util.UserStatusManager;

public abstract class BaseActivity extends AppCompatActivity implements IncomingInviteDialog.InviteResponseListener {

    private static final FirebaseDatabase rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/");

    private final NotificationRepository notificationRepository = new NotificationRepository();

    private DatabaseReference cancelListenerRef;
    private ValueEventListener cancelListener;

    private DatabaseReference matchIdListenerRef;
    private ValueEventListener matchIdListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        InviteManager.get().getIncomingInvite().observe(this, data -> {
            if (data == null) return;
            String inviteId = data[0];
            String fromUid  = data[1];
            if (inviteId.equals(InviteManager.get().getShownInviteId())) return;
            InviteManager.get().setShownInviteId(inviteId);
            listenForCancellation(inviteId);
            loadInviterProfileThenShowDialog(inviteId, fromUid);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeCancelListener();
        removeMatchIdListener();
    }

    private void listenForCancellation(String inviteId) {
        removeCancelListener();
        cancelListenerRef = rtdb.getReference("invites").child(inviteId);
        cancelListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if ("cancelled".equals(status) || "expired".equals(status)) {
                    InviteManager.get().clearIncomingInvite();

                    Fragment f = getSupportFragmentManager().findFragmentByTag("incomingInvite");
                    if (f instanceof IncomingInviteDialog) ((IncomingInviteDialog) f).dismissAllowingStateLoss();
                    removeCancelListener();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        cancelListenerRef.addValueEventListener(cancelListener);
    }

    private void removeCancelListener() {
        if (cancelListenerRef != null && cancelListener != null) cancelListenerRef.removeEventListener(cancelListener);

        cancelListenerRef = null;
        cancelListener = null;
    }

    private void removeMatchIdListener() {
        if (matchIdListenerRef != null && matchIdListener != null) matchIdListenerRef.removeEventListener(matchIdListener);

        matchIdListenerRef = null;
        matchIdListener    = null;
    }

    private void loadInviterProfileThenShowDialog(String inviteId, String fromUid) {
        FirebaseFirestore.getInstance()
                .collection("users").document(fromUid).get()
                .addOnSuccessListener(userDoc -> {
                    String username = userDoc.getString("username");
                    FirebaseFirestore.getInstance()
                            .collection("profiles").document(fromUid).get()
                            .addOnSuccessListener(profileDoc -> {
                                String avatarUrl = profileDoc.getString("avatarUrl");
                                showDialog(inviteId, username != null ? username : "Someone", avatarUrl);
                            })
                            .addOnFailureListener(e -> showDialog(inviteId, username != null ? username : "Someone", null));
                })
                .addOnFailureListener(e -> showDialog(inviteId, "Someone", null));
    }

    private void showDialog(String inviteId, String username, String avatarUrl) {
        if (isFinishing() || isDestroyed()) return;
        if (getSupportFragmentManager().findFragmentByTag("incomingInvite") != null) return;
        IncomingInviteDialog dialog = IncomingInviteDialog.newInstance(inviteId, username, avatarUrl);
        dialog.setResponseListener(this);
        dialog.show(getSupportFragmentManager(), "incomingInvite");
    }

    @Override
    public void onAccept(String inviteId) {
        InviteManager.get().setShownInviteId(null);
        InviteManager.get().clearIncomingInvite();
        removeCancelListener();

        DatabaseReference inviteRef = rtdb.getReference("invites").child(inviteId);
        inviteRef.child("status").setValue("accepted");

        removeMatchIdListener();
        matchIdListenerRef = inviteRef;
        matchIdListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String matchId = snapshot.child("matchId").getValue(String.class);
                if (matchId == null || matchId.isEmpty()) return;

                String fromUsername = snapshot.child("fromUsername").getValue(String.class);
                String toUsername = snapshot.child("toUsername").getValue(String.class);

                String notifId = snapshot.child("notifId").getValue(String.class);
                if (notifId != null) {
                    notificationRepository.respondToMatchInvite(notifId, "accepted",
                            new RepositoryCallback<Void>() {
                                @Override public void onSuccess(Void v) {}
                                @Override public void onFailure(Exception e) {}
                            });
                }

                removeMatchIdListener();
                if (isFinishing() || isDestroyed()) return;

                UserStatusManager.setInGame(FirebaseAuth.getInstance(), true);

                android.content.Intent intent = new android.content.Intent(BaseActivity.this, com.slagalica.app.ui.match.MatchmakingActivity.class);
                intent.putExtra("inviteMatchId", matchId);
                intent.putExtra("isPlayer1", false);
                intent.putExtra("username", toUsername != null ? toUsername : "Player");
                intent.putExtra("opponentUsername", fromUsername != null ? fromUsername : "Opponent");
                startActivity(intent);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        matchIdListenerRef.addValueEventListener(matchIdListener);
    }

    @Override
    public void onDecline(String inviteId) {
        InviteManager.get().setShownInviteId(null);
        InviteManager.get().clearIncomingInvite();
        removeCancelListener();

        DatabaseReference inviteRef = rtdb.getReference("invites").child(inviteId);
        inviteRef.get().addOnSuccessListener(snapshot -> {
            String notifId = snapshot.child("notifId").getValue(String.class);
            if (notifId != null) {
                notificationRepository.respondToMatchInvite(notifId, "declined",
                        new RepositoryCallback<Void>() {
                            @Override public void onSuccess(Void v) {}
                            @Override public void onFailure(Exception e) {}
                        });
            }
        });

        rtdb.getReference("invites").child(inviteId).child("status").setValue("declined");
    }
}