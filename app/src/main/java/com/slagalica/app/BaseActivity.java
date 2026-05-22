package com.slagalica.app;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.util.InviteManager;
import com.slagalica.app.util.IncomingInviteDialog;

public abstract class BaseActivity extends AppCompatActivity implements IncomingInviteDialog.InviteResponseListener {

    private static final FirebaseDatabase rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/");

    private DatabaseReference cancelListenerRef;
    private ValueEventListener cancelListener;

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

        rtdb.getReference("invites").child(inviteId).child("status").setValue("accepted");
        startActivity(new android.content.Intent(this, com.slagalica.app.ui.match.MatchmakingActivity.class));
    }

    @Override
    public void onDecline(String inviteId) {
        InviteManager.get().setShownInviteId(null);
        InviteManager.get().clearIncomingInvite();
        removeCancelListener();

        rtdb.getReference("invites").child(inviteId).child("status").setValue("declined");
    }
}