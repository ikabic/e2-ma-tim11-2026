package com.slagalica.app.ui.tournament;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.R;
import com.slagalica.app.ui.HomeActivity;

public class TournamentPodiumActivity extends AppCompatActivity {

    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament_podium);

        String winnerUid = getIntent().getStringExtra("winnerUid");
        String winnerName = getIntent().getStringExtra("winnerName");
        String finalistUid = getIntent().getStringExtra("finalistUid");
        String finalistName = getIntent().getStringExtra("finalistName");
        String sf1LoserUid = getIntent().getStringExtra("sf1LoserUid");
        String sf2LoserUid = getIntent().getStringExtra("sf2LoserUid");
        myUid  = getIntent().getStringExtra("myUid");

        View bannerWin  = findViewById(R.id.bannerWin);
        View bannerLoss = findViewById(R.id.bannerLoss);
        View bannerElim = findViewById(R.id.bannerElim);

        if (myUid != null && myUid.equals(winnerUid)) {
            bannerWin.setVisibility(View.VISIBLE);
        } else if (myUid != null && myUid.equals(finalistUid)) {
            bannerLoss.setVisibility(View.VISIBLE);
        } else {
            bannerElim.setVisibility(View.VISIBLE);
        }

        bindPlayerCard(R.id.ivWinnerAvatar, R.id.tvWinnerName, R.id.tvWinnerLeague, winnerUid, winnerName);

        bindPlayerCard(R.id.ivFinalistAvatar, R.id.tvFinalistName, R.id.tvFinalistLeague, finalistUid, finalistName);

        bindPlayerCard(R.id.ivSf1Avatar, R.id.tvSf1Name, R.id.tvSf1League, sf1LoserUid, null);
        bindPlayerCard(R.id.ivSf2Avatar, R.id.tvSf2Name, R.id.tvSf2League, sf2LoserUid, null);

        MaterialButton btnGoHome = findViewById(R.id.btnGoHome);
        btnGoHome.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
    }

    private void bindPlayerCard(int avatarResId, int nameResId, int leagueResId, String uid, String knownName) {
        ImageView ivAvatar = findViewById(avatarResId);
        TextView tvName = findViewById(nameResId);
        TextView tvLeague = findViewById(leagueResId);

        if (uid == null || uid.isEmpty() || uid.equals("unknown")) {
            if (tvName != null) tvName.setText(knownName != null ? knownName : "?");
            return;
        }

        if (tvName != null && knownName != null) tvName.setText(knownName);

        FirebaseFirestore.getInstance().collection("profiles").document(uid).get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    if (!doc.exists()) return;

                    String avatarUrl = doc.getString("avatarUrl");
                    if (ivAvatar != null) {
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            Glide.with(this).load(avatarUrl).placeholder(R.drawable.ic_nav_profile).circleCrop().into(ivAvatar);
                        } else {
                            ivAvatar.setImageResource(R.drawable.ic_nav_profile);
                        }
                    }

                    Long stars = doc.getLong("stars");
                    String league = leagueForStars(stars != null ? stars : 0);
                    if (tvLeague != null) tvLeague.setText(league);

                    if (knownName == null) {
                        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                                .addOnSuccessListener(userDoc -> {
                                    String username = userDoc.getString("username");
                                    if (tvName != null && username != null)
                                        tvName.setText(username);
                                });
                    }
                });
    }

    private String leagueForStars(long stars) {
        if (stars < 50)   return "Unranked";
        if (stars < 200)  return "Bronze";
        if (stars < 500)  return "Silver";
        if (stars < 1000) return "Gold";
        if (stars < 2000) return "Platinum";
        return "Diamond";
    }
}