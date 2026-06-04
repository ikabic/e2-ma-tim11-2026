package com.slagalica.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.BaseActivity;
import com.slagalica.app.R;
import com.slagalica.app.adapter.RegionRankingAdapter;
import com.slagalica.app.databinding.ActivityHomeBinding;
import com.slagalica.app.model.Region;
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
import com.slagalica.app.ui.ranking.RankingAdapter;
import com.slagalica.app.ui.regions.RegionFragment;
import com.slagalica.app.viewmodel.NotificationViewModel;
import com.slagalica.app.viewmodel.RankingViewModel;
import com.slagalica.app.viewmodel.RegionViewModel;

import android.os.Handler;
import android.os.Looper;

import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends BaseActivity {

    private String playerUsername = "You";
    private ActivityHomeBinding binding;
    private NotificationViewModel notifViewModel;
    private RankingViewModel rankingViewModel;
    private RankingAdapter rankingAdapter;
    private RegionRankingAdapter regionRankingAdapter;
    private RegionViewModel regionViewModel;
    private Handler countdownHandler  = new Handler(Looper.getMainLooper());
    private long nextRefreshAtMs = 0;
    private String userRegionKey = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.regionFragmentContainer, new RegionFragment())
                    .commit();

        regionRankingAdapter = new RegionRankingAdapter();
        regionViewModel = new ViewModelProvider(this).get(RegionViewModel.class);
        regionViewModel.bootstrapRegions(this);

        rankingAdapter = new RankingAdapter();
        binding.rvRanking.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRanking.setAdapter(rankingAdapter);

        rankingViewModel = new ViewModelProvider(this).get(RankingViewModel.class);

        rankingViewModel.getEntries().observe(this, list -> {
            if (rankingViewModel.getActiveType().getValue() == RankingViewModel.CycleType.REGIONAL)
                return;

            boolean empty = list == null || list.isEmpty();
            binding.rvRanking.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.layoutRankEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (!empty) rankingAdapter.submitList(list);
            nextRefreshAtMs = System.currentTimeMillis() + 2 * 60 * 1000L;
        });

        rankingViewModel.getLoading().observe(this, isLoading ->
                binding.pbRankLoading.setVisibility(isLoading != null && isLoading ? View.VISIBLE : View.GONE));

        rankingViewModel.getCycle().observe(this, cycle -> {
            if (cycle != null && cycle.getStartDate() != null && cycle.getEndDate() != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault());
                String range = sdf.format(cycle.getStartDate().toDate()) + " – " + sdf.format(cycle.getEndDate().toDate());
                binding.tvCycleDateRange.setText(range);
            }
        });

        rankingViewModel.getActiveType().observe(this, type -> {
            setTabActive(binding.btnTabWeekly, type == RankingViewModel.CycleType.WEEKLY);
            setTabActive(binding.btnTabMonthly, type == RankingViewModel.CycleType.MONTHLY);
            setTabActive(binding.btnTabRegional, type == RankingViewModel.CycleType.REGIONAL);

            if (type == RankingViewModel.CycleType.REGIONAL) {
                if (regionRankingAdapter == null)
                    regionRankingAdapter = new RegionRankingAdapter();
                binding.rvRanking.setAdapter(regionRankingAdapter);
                regionViewModel.fetchLeaderboard();
            } else {
                binding.rvRanking.setAdapter(rankingAdapter);
                rankingViewModel.refresh();
            }
        });

        regionViewModel.getLeaderboard().observe(this, list -> {
            if (rankingViewModel.getActiveType().getValue() == RankingViewModel.CycleType.REGIONAL)
                displayRegionalList(list);
        });

        binding.btnTabWeekly.setOnClickListener(v ->
                rankingViewModel.selectType(RankingViewModel.CycleType.WEEKLY));
        binding.btnTabMonthly.setOnClickListener(v ->
                rankingViewModel.selectType(RankingViewModel.CycleType.MONTHLY));
        binding.btnTabRegional.setOnClickListener(v ->
                rankingViewModel.selectType(RankingViewModel.CycleType.REGIONAL));

        nextRefreshAtMs = System.currentTimeMillis() + 2 * 60 * 1000L;
        countdownHandler.post(countdownTick);

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

                        String region = doc.getString("region");
                        userRegionKey = region != null ? region : "";

                        if (rankingViewModel.getActiveType().getValue() == RankingViewModel.CycleType.REGIONAL)
                            displayRegionalList(regionViewModel.getLeaderboard().getValue());
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
        binding.navBtnRanks.setOnClickListener(v -> selectTab(2));
        binding.navBtnRegions.setOnClickListener(v -> selectTab(3));
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

    private void displayRegionalList(List<Region> list) {
        boolean empty = list == null || list.isEmpty();
        binding.rvRanking.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.layoutRankEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (!empty)
            regionRankingAdapter.submitList(list, userRegionKey);
    }

    private void selectTab(int index) {
        binding.sectionHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        binding.sectionGames.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        binding.sectionRanks.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        binding.regionFragmentContainer.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        countdownHandler.removeCallbacks(countdownTick);
    }

    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            long remaining = nextRefreshAtMs - System.currentTimeMillis();
            if (remaining < 0) remaining = 0;
            long min = remaining / 60000;
            long sec = (remaining % 60000) / 1000;
            binding.tvRankRefreshCountdown.setText(String.format(java.util.Locale.getDefault(), "↻ %d:%02d", min, sec));
            countdownHandler.postDelayed(this, 1000);
        }
    };

    private void setTabActive(MaterialButton btn, boolean active) {
        if (active) {
            btn.setBackgroundTintList(getResources().getColorStateList(R.color.accent, null));
            btn.setTextColor(getResources().getColor(R.color.accent_ink, null));
            btn.setStrokeColor(getResources().getColorStateList(R.color.accent, null));
        } else {
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btn.setTextColor(getResources().getColor(R.color.text_mute, null));
            btn.setStrokeColor(getResources().getColorStateList(R.color.border, null));
        }
    }
}
