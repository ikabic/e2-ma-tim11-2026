package com.slagalica.app.ui.tournament;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.button.MaterialButton;
import com.slagalica.app.R;
import com.slagalica.app.model.Profile;
import com.slagalica.app.repository.RepositoryCallback;
import com.slagalica.app.repository.TournamentRepository;

public class TournamentMatchmakingActivity extends AppCompatActivity {

    private TournamentRepository repo;
    private String myUid;
    private String myUsername;
    private String myAvatarUrl;
    private String myLeague;
    private boolean matchFound = false;
    private View sectionSearching;
    private MaterialButton btnCancel;
    private View sectionPairing;
    private TextView tvPairA1Name, tvPairA2Name, tvPairB1Name, tvPairB2Name;
    private TextView tvPairA1League, tvPairA2League, tvPairB1League, tvPairB2League;
    private ImageView ivPairA1Avatar, ivPairA2Avatar, ivPairB1Avatar, ivPairB2Avatar;
    private TextView tvYourMatchLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament_matchmaking);

        myUsername = getIntent().getStringExtra("username");
        myAvatarUrl = getIntent().getStringExtra("avatarUrl");
        myLeague = getIntent().getStringExtra("league");
        if (myUsername  == null) myUsername = "Player";
        if (myAvatarUrl == null) myAvatarUrl = "";
        if (myLeague == null) myLeague = "Unranked";

        repo  = new TournamentRepository();
        myUid = repo.getUid();

        if (myLeague.equals("Unranked") && myUid != null) {
            FirebaseFirestore.getInstance().collection("profiles").document(myUid).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Profile p = doc.toObject(Profile.class);
                            if (p != null) myLeague = p.getLeague(null);
                        }
                    });
        }

        initViews();

        repo.joinQueue(myUsername, myAvatarUrl, myLeague,
                this::onTournamentFound,
                new RepositoryCallback<Void>() {
                    @Override public void onSuccess(Void v) {}
                    @Override public void onFailure(Exception e) {
                        Toast.makeText(TournamentMatchmakingActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }

    private void initViews() {
        sectionSearching = findViewById(R.id.sectionSearching);
        sectionPairing  = findViewById(R.id.sectionPairing);
        btnCancel = findViewById(R.id.btnCancel);

        tvPairA1Name = findViewById(R.id.tvPairA1Name);
        tvPairA2Name = findViewById(R.id.tvPairA2Name);
        tvPairB1Name = findViewById(R.id.tvPairB1Name);
        tvPairB2Name = findViewById(R.id.tvPairB2Name);

        tvPairA1League = findViewById(R.id.tvPairA1League);
        tvPairA2League = findViewById(R.id.tvPairA2League);
        tvPairB1League = findViewById(R.id.tvPairB1League);
        tvPairB2League = findViewById(R.id.tvPairB2League);

        ivPairA1Avatar = findViewById(R.id.ivPairA1Avatar);
        ivPairA2Avatar = findViewById(R.id.ivPairA2Avatar);
        ivPairB1Avatar = findViewById(R.id.ivPairB1Avatar);
        ivPairB2Avatar = findViewById(R.id.ivPairB2Avatar);

        tvYourMatchLabel = findViewById(R.id.tvYourMatchLabel);

        btnCancel.setOnClickListener(v -> {
            repo.leaveQueue(myUid);
            finish();
        });
    }

    private void onTournamentFound(String tournamentId, String semifinal, boolean isPlayer1, String matchId, String opponentUsername, DataSnapshot playersSnap) {
        if (matchFound) return;
        matchFound = true;
        String myUid = repo.getUid();
        String opponentUid = "";

        for (DataSnapshot player : playersSnap.getChildren()) {
            String uid = player.getKey();
            String sf = player.child("semifinal").getValue(String.class);
            if (uid != null && !uid.equals(myUid) && semifinal.equals(sf)) {
                opponentUid = uid;
                break;
            }
        }

        final String finalOpponentUid = opponentUid;

        runOnUiThread(() -> {
            String[] names   = new String[4];
            String[] leagues = new String[4];
            String[] avatars = new String[4];
            String[] semis   = new String[4];
            int aIdx = 0, bIdx = 2;

            for (DataSnapshot player : playersSnap.getChildren()) {
                String sf   = player.child("semifinal").getValue(String.class);
                Object uObj = player.child("username").getValue();
                Object lgObj = player.child("league").getValue();
                Object avObj = player.child("avatarUrl").getValue();
                String uname = uObj  != null ? uObj.toString()  : "Player";
                String lg = lgObj != null ? lgObj.toString() : "Unranked";
                String av = avObj != null ? avObj.toString() : "";

                if ("A".equals(sf) && aIdx < 2) {
                    names[aIdx]   = uname;
                    leagues[aIdx] = lg;
                    avatars[aIdx] = av;
                    aIdx++;
                } else if ("B".equals(sf) && bIdx < 4) {
                    names[bIdx]   = uname;
                    leagues[bIdx] = lg;
                    avatars[bIdx] = av;
                    bIdx++;
                }
            }

            sectionSearching.setVisibility(View.GONE);
            sectionPairing.setVisibility(View.VISIBLE);

            setPlayerCard(tvPairA1Name, tvPairA1League, ivPairA1Avatar, names[0], leagues[0], avatars[0]);
            setPlayerCard(tvPairA2Name, tvPairA2League, ivPairA2Avatar, names[1], leagues[1], avatars[1]);
            setPlayerCard(tvPairB1Name, tvPairB1League, ivPairB1Avatar, names[2], leagues[2], avatars[2]);
            setPlayerCard(tvPairB2Name, tvPairB2League, ivPairB2Avatar, names[3], leagues[3], avatars[3]);

            String yourPairLabel = "A".equals(semifinal) ? "Your match: " + names[0] + " vs " + names[1] : "Your match: " + names[2] + " vs " + names[3];
            tvYourMatchLabel.setText(yourPairLabel);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent intent = new Intent(this, TournamentMatchActivity.class);
                intent.putExtra("tournamentId", tournamentId);
                intent.putExtra("semifinal", semifinal);
                intent.putExtra("matchId", matchId);
                intent.putExtra("isPlayer1", isPlayer1);
                intent.putExtra("username", myUsername);
                intent.putExtra("opponentUsername", opponentUsername);
                intent.putExtra("opponentUid", finalOpponentUid);
                startActivity(intent);
                finish();
            }, 3000);
        });
    }

    private void setPlayerCard(TextView tvName, TextView tvLeague, ImageView ivAvatar, String name, String league, String avatarUrl) {
        if (tvName != null) tvName.setText(name   != null ? name   : "?");
        if (tvLeague != null) tvLeague.setText(league != null ? league : "Unranked");
        if (ivAvatar != null) {
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Glide.with(this).load(avatarUrl).placeholder(R.drawable.ic_nav_profile).circleCrop().into(ivAvatar);
            } else {
                ivAvatar.setImageResource(R.drawable.ic_nav_profile);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!matchFound) {
            repo.leaveQueue(myUid);
        }
    }
}