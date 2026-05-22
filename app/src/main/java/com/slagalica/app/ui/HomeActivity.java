package com.slagalica.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.R;
import com.slagalica.app.ui.game.asocijacije.AsocijacijeActivity;
import com.slagalica.app.ui.game.koznazna.KoZnaZnaActivity;
import com.slagalica.app.ui.game.korakpokorak.KorakPoKorakActivity;
import com.slagalica.app.ui.game.mojbroj.MojBrojActivity;
import com.slagalica.app.ui.game.skocko.SkockoActivity;
import com.slagalica.app.ui.game.spojnice.SpojniceActivity;
import com.slagalica.app.ui.match.MatchmakingActivity;
import com.slagalica.app.ui.auth.LoginActivity;
import com.slagalica.app.ui.notifications.NotificationsActivity;
import com.slagalica.app.ui.profile.ProfileActivity;
import com.slagalica.app.viewmodel.NotificationViewModel;

public class HomeActivity extends AppCompatActivity {

    private String playerUsername = "You";
    private TextView tvNotifBadge;
    private FrameLayout frameBell;
    private NotificationViewModel notifViewModel;

    private LinearLayout sectionHome;
    private LinearLayout sectionGames;

    private LinearLayout navBtnHome;
    private LinearLayout navBtnGames;
    private LinearLayout navBtnRanks;
    private LinearLayout navBtnRegions;
    private LinearLayout navBtnProfile;

    private ImageView navIconHome, navIconGames, navIconRanks, navIconRegions, navIconProfile;
    private TextView navLabelHome, navLabelGames, navLabelRanks, navLabelRegions, navLabelProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sectionHome  = findViewById(R.id.sectionHome);
        sectionGames = findViewById(R.id.sectionGames);

        navBtnHome    = findViewById(R.id.navBtnHome);
        navBtnGames   = findViewById(R.id.navBtnGames);
        navBtnRanks   = findViewById(R.id.navBtnRanks);
        navBtnRegions = findViewById(R.id.navBtnRegions);
        navBtnProfile = findViewById(R.id.navBtnProfile);

        navIconHome    = findViewById(R.id.navIconHome);
        navIconGames   = findViewById(R.id.navIconGames);
        navIconRanks   = findViewById(R.id.navIconRanks);
        navIconRegions = findViewById(R.id.navIconRegions);
        navIconProfile = findViewById(R.id.navIconProfile);

        navLabelHome    = findViewById(R.id.navLabelHome);
        navLabelGames   = findViewById(R.id.navLabelGames);
        navLabelRanks   = findViewById(R.id.navLabelRanks);
        navLabelRegions = findViewById(R.id.navLabelRegions);
        navLabelProfile = findViewById(R.id.navLabelProfile);

        TextView tvWelcome    = findViewById(R.id.tvWelcome);
        TextView tvTokenCount = findViewById(R.id.tvTokenCount);
        TextView tvStarCount  = findViewById(R.id.tvStarCount);
        TextView tvTokenInfo  = findViewById(R.id.tvTokenInfo);
        LinearLayout chipTokensHeader = findViewById(R.id.chipTokensHeader);
        LinearLayout chipStarsHeader  = findViewById(R.id.chipStarsHeader);
        LinearLayout rowTokenInfo     = findViewById(R.id.rowTokenInfo);
        MaterialButton btnGuestLogin  = findViewById(R.id.btnGuestLogin);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isAnonymous()) {
                playerUsername = "Guest";
                tvWelcome.setText("Guest");
                chipTokensHeader.setVisibility(View.GONE);
                chipStarsHeader.setVisibility(View.GONE);
                rowTokenInfo.setVisibility(View.GONE);
                btnGuestLogin.setVisibility(View.VISIBLE);
                btnGuestLogin.setOnClickListener(v -> {
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                });
            } else {
                FirebaseFirestore db = FirebaseFirestore.getInstance();

                db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        String username = doc.getString("username");
                        playerUsername = username != null ? username : currentUser.getEmail();
                        tvWelcome.setText(playerUsername);
                    })
                    .addOnFailureListener(e -> {
                        playerUsername = currentUser.getEmail();
                        tvWelcome.setText(playerUsername);
                    });

                db.collection("profiles").document(currentUser.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Long tokens = doc.getLong("tokens");
                            Long stars  = doc.getLong("stars");
                            int t = tokens != null ? tokens.intValue() : 5;
                            int s = stars  != null ? stars.intValue()  : 0;
                            tvTokenCount.setText(String.valueOf(t));
                            tvStarCount.setText(String.valueOf(s));
                            tvTokenInfo.setText(t + " left");
                        }
                    });
            }
        }

        // Notification bell
        frameBell    = findViewById(R.id.frameBell);
        tvNotifBadge = findViewById(R.id.tvNotifBadge);
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

        // Game card click listeners
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

        MaterialButton btnFindMatch = findViewById(R.id.btnFindMatch);
        btnFindMatch.setOnClickListener(v -> {
            Intent i = new Intent(HomeActivity.this, MatchmakingActivity.class);
            i.putExtra("username", playerUsername);
            startActivity(i);
        });

        // Bottom navigation
        boolean isGuest = currentUser != null && currentUser.isAnonymous();
        navBtnHome.setOnClickListener(v -> selectTab(0));
        navBtnGames.setOnClickListener(v -> selectTab(1));
        navBtnRanks.setOnClickListener(v -> { /* placeholder — coming soon */ });
        navBtnRegions.setOnClickListener(v -> { /* placeholder — coming soon */ });
        navBtnProfile.setOnClickListener(v -> {
            if (isGuest) {
                android.widget.Toast.makeText(this,
                    "Register to access your profile", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(this, ProfileActivity.class));
            }
        });

        selectTab(0); // start on Home
    }

    private void selectTab(int index) {
        // Show/hide sections
        sectionHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        sectionGames.setVisibility(index == 1 ? View.VISIBLE : View.GONE);

        // Update icon tints and label colours
        int accent = ContextCompat.getColor(this, R.color.accent);
        int mute   = ContextCompat.getColor(this, R.color.text_mute);

        navIconHome.setImageTintList(android.content.res.ColorStateList.valueOf(index == 0 ? accent : mute));
        navIconGames.setImageTintList(android.content.res.ColorStateList.valueOf(index == 1 ? accent : mute));
        navIconRanks.setImageTintList(android.content.res.ColorStateList.valueOf(index == 2 ? accent : mute));
        navIconRegions.setImageTintList(android.content.res.ColorStateList.valueOf(index == 3 ? accent : mute));
        navIconProfile.setImageTintList(android.content.res.ColorStateList.valueOf(index == 4 ? accent : mute));

        navLabelHome.setTextColor(index == 0 ? accent : mute);
        navLabelGames.setTextColor(index == 1 ? accent : mute);
        navLabelRanks.setTextColor(index == 2 ? accent : mute);
        navLabelRegions.setTextColor(index == 3 ? accent : mute);
        navLabelProfile.setTextColor(index == 4 ? accent : mute);
    }
}
