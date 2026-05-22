package com.slagalica.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.BaseActivity;
import com.slagalica.app.R;
import com.slagalica.app.databinding.ActivityHomeBinding;
import com.slagalica.app.ui.game.asocijacije.AsocijacijeActivity;
import com.slagalica.app.ui.game.koznazna.KoZnaZnaActivity;
import com.slagalica.app.ui.game.korakpokorak.KorakPoKorakActivity;
import com.slagalica.app.ui.game.mojbroj.MojBrojActivity;
import com.slagalica.app.ui.game.skocko.SkockoActivity;
import com.slagalica.app.ui.game.spojnice.SpojniceActivity;
import com.slagalica.app.ui.match.MatchmakingActivity;
import com.slagalica.app.ui.auth.LoginActivity;
import com.slagalica.app.ui.notifications.NotificationsActivity;
import com.slagalica.app.ui.profile.FriendsActivity;
import com.slagalica.app.ui.profile.ProfileActivity;
import com.slagalica.app.viewmodel.NotificationViewModel;

public class HomeActivity extends BaseActivity {

    private String playerUsername = "You";
    private ActivityHomeBinding binding;
    private NotificationViewModel notifViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            if (currentUser.isAnonymous()) {
                playerUsername = "Guest";
                binding.tvWelcome.setText("Guest");
                binding.chipTokensHeader.setVisibility(View.GONE);
                binding.chipStarsHeader.setVisibility(View.GONE);
                binding.rowTokenInfo.setVisibility(View.GONE);
                binding.btnGuestLogin.setVisibility(View.VISIBLE);
                binding.btnGuestLogin.setOnClickListener(v -> {
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
                        binding.tvWelcome.setText(playerUsername);
                    })
                    .addOnFailureListener(e -> {
                        playerUsername = currentUser.getEmail();
                        binding.tvWelcome.setText(playerUsername);
                    });

                db.collection("profiles").document(currentUser.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Long tokens = doc.getLong("tokens");
                            Long stars  = doc.getLong("stars");
                            int t = tokens != null ? tokens.intValue() : 5;
                            int s = stars  != null ? stars.intValue()  : 0;
                            binding.tvTokenCount.setText(String.valueOf(t));
                            binding.tvStarCount.setText(String.valueOf(s));
                            binding.tvTokenInfo.setText(t + " left");
                        }
                    });
            }
        }

        binding.btnFriends.setOnClickListener(v ->
                startActivity(new Intent(this, FriendsActivity.class))
        );

        notifViewModel = new ViewModelProvider(this).get(NotificationViewModel.class);
        notifViewModel.getUnreadCount().observe(this, count -> {
            if (count != null && count > 0) {
                binding.tvNotifBadge.setVisibility(View.VISIBLE);
                binding.tvNotifBadge.setText(count > 9 ? "9+" : String.valueOf(count));
            } else {
                binding.tvNotifBadge.setVisibility(View.GONE);
            }
        });

        View.OnClickListener openNotifs = v ->
                startActivity(new Intent(this, NotificationsActivity.class));
        binding.frameBell.setOnClickListener(openNotifs);
        binding.btnNotifications.setOnClickListener(openNotifs);

        // Game card click listeners
        binding.cardKorakPoKorak.setOnClickListener(v -> {
            Intent i = new Intent(this, KorakPoKorakActivity.class);
            i.putExtra("username", playerUsername);
            startActivity(i);
        });

        binding.cardMojBroj.setOnClickListener(v -> {
            Intent i = new Intent(this, MojBrojActivity.class);
            i.putExtra("username", playerUsername);
            startActivity(i);
        });

        binding.cardAsocijacije.setOnClickListener(v ->
                startActivity(new Intent(this, AsocijacijeActivity.class))
        );

        binding.cardSkocko.setOnClickListener(v ->
                startActivity(new Intent(this, SkockoActivity.class))
        );

        binding.cardKoZnaZna.setOnClickListener(v ->
                startActivity(new Intent(this, KoZnaZnaActivity.class))
        );

        binding.cardSpojnice.setOnClickListener(v ->
                startActivity(new Intent(this, SpojniceActivity.class))
        );

        binding.btnFindMatch.setOnClickListener(v -> {
            Intent i = new Intent(HomeActivity.this, MatchmakingActivity.class);
            i.putExtra("username", playerUsername);
            startActivity(i);
        });

        // Bottom navigation
        boolean isGuest = currentUser != null && currentUser.isAnonymous();
        binding.navBtnHome.setOnClickListener(v -> selectTab(0));
        binding.navBtnGames.setOnClickListener(v -> selectTab(1));
        binding.navBtnRanks.setOnClickListener(v -> { /* placeholder — coming soon */ });
        binding.navBtnRegions.setOnClickListener(v -> { /* placeholder — coming soon */ });
        binding.navBtnProfile.setOnClickListener(v -> {
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
        binding.sectionHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        binding.sectionGames.setVisibility(index == 1 ? View.VISIBLE : View.GONE);

        // Update icon tints and label colours
        int accent = ContextCompat.getColor(this, R.color.accent);
        int mute   = ContextCompat.getColor(this, R.color.text_mute);

        binding.navIconHome.setImageTintList(android.content.res.ColorStateList.valueOf(index == 0 ? accent : mute));
        binding.navIconGames.setImageTintList(android.content.res.ColorStateList.valueOf(index == 1 ? accent : mute));
        binding.navIconRanks.setImageTintList(android.content.res.ColorStateList.valueOf(index == 2 ? accent : mute));
        binding.navIconRegions.setImageTintList(android.content.res.ColorStateList.valueOf(index == 3 ? accent : mute));
        binding.navIconProfile.setImageTintList(android.content.res.ColorStateList.valueOf(index == 4 ? accent : mute));

        binding.navLabelHome.setTextColor(index == 0 ? accent : mute);
        binding.navLabelGames.setTextColor(index == 1 ? accent : mute);
        binding.navLabelRanks.setTextColor(index == 2 ? accent : mute);
        binding.navLabelRegions.setTextColor(index == 3 ? accent : mute);
        binding.navLabelProfile.setTextColor(index == 4 ? accent : mute);
    }
}
