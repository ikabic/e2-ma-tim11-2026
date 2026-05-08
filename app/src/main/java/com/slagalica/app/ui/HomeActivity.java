package com.slagalica.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.slagalica.app.ui.game.koznazna.KoZnaZnaActivity;
import com.slagalica.app.ui.game.spojnice.SpojniceActivity;
import com.slagalica.app.util.ConfirmDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.R;
import com.slagalica.app.ui.auth.ChangePasswordActivity;
import com.slagalica.app.ui.game.korakpokorak.KorakPoKorakActivity;
import com.slagalica.app.ui.game.mojbroj.MojBrojActivity;
import com.slagalica.app.ui.game.asocijacije.AsocijacijeActivity;
import com.slagalica.app.ui.auth.LoginActivity;
import com.slagalica.app.ui.game.skocko.SkockoActivity;
import com.slagalica.app.ui.profile.ProfileActivity;
import com.slagalica.app.viewmodel.AuthViewModel;
import com.slagalica.app.viewmodel.NotificationViewModel;
import com.slagalica.app.ui.notifications.NotificationsActivity;

public class HomeActivity extends AppCompatActivity {

    private String playerUsername = "You";
    private TextView    tvNotifBadge;
    private FrameLayout frameBell;
    private NotificationViewModel notifViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isAnonymous()) {
                playerUsername = "Guest";
                tvWelcome.setText("Guest");
            } else {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        String username = doc.getString("username");
                        playerUsername = username != null ? username : currentUser.getEmail();
                        tvWelcome.setText(playerUsername);
                    })
                    .addOnFailureListener(e -> {
                        playerUsername = currentUser.getEmail();
                        tvWelcome.setText(playerUsername);
                    });

                tvWelcome.setOnClickListener(v ->
                        startActivity(new Intent(HomeActivity.this, ProfileActivity.class))
                );
            }
        }

        frameBell = findViewById(R.id.frameBell);
        tvNotifBadge  = findViewById(R.id.tvNotifBadge);
        MaterialButton btnNotifications = findViewById(R.id.btnNotifications);

        notifViewModel = new ViewModelProvider(this).get(NotificationViewModel.class);
        notifViewModel.getUnreadCount().observe(this, count -> {
            if (count != null && count > 0) {
                tvNotifBadge.setVisibility(View.VISIBLE);
                tvNotifBadge.setText(count > 9 ? "9+" : String.valueOf(count));
            } else {
                tvNotifBadge.setVisibility(View.GONE);
            }
        });

        View.OnClickListener openNotifs = v ->
                startActivity(new Intent(this, NotificationsActivity.class));
        frameBell.setOnClickListener(openNotifs);
        btnNotifications.setOnClickListener(openNotifs);

        MaterialCardView cardKorakPoKorak = findViewById(R.id.cardKorakPoKorak);
        cardKorakPoKorak.setOnClickListener(v -> {
            Intent i = new Intent(this, KorakPoKorakActivity.class);
            i.putExtra("username", playerUsername);
            startActivity(i);
        });

        MaterialCardView cardMojBroj = findViewById(R.id.cardMojBroj);
        cardMojBroj.setOnClickListener(v -> {
            Intent i = new Intent(this, MojBrojActivity.class);
            i.putExtra("username", playerUsername);
            startActivity(i);
        });

        MaterialCardView cardAsocijacije = findViewById(R.id.cardAsocijacije);
        cardAsocijacije.setOnClickListener(v ->
                startActivity(new Intent(this, AsocijacijeActivity.class))
        );

        MaterialCardView cardSkocko = findViewById(R.id.cardSkocko);
        cardSkocko.setOnClickListener(v ->
                startActivity(new Intent(this, SkockoActivity.class))
        );

        MaterialCardView cardKoZnaZna = findViewById(R.id.cardKoZnaZna);
        cardKoZnaZna.setOnClickListener(v ->
                startActivity(new Intent(this, KoZnaZnaActivity.class))
        );

        MaterialCardView cardSpojnice = findViewById(R.id.cardSpojnice);
        cardSpojnice.setOnClickListener(v ->
                startActivity(new Intent(this, SpojniceActivity.class))
        );

        MaterialButton btnChangePassword = findViewById(R.id.btnChangePassword);
        if (currentUser != null && currentUser.isAnonymous()) {
            btnChangePassword.setVisibility(android.view.View.GONE);
        } else {
            btnChangePassword.setOnClickListener(v ->
                startActivity(new Intent(this, ChangePasswordActivity.class))
            );
        }

        AuthViewModel authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v ->
            ConfirmDialog.show(this, "Log out?", "You'll need to sign in again.",
                "Log out", "Cancel", () -> {
                    authViewModel.logout();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
        );
    }
}
