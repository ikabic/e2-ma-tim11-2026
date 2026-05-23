package com.slagalica.app.ui.match;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.button.MaterialButton;
import com.slagalica.app.R;
import com.slagalica.app.repository.MatchRepository;
import com.slagalica.app.repository.RepositoryCallback;
import com.slagalica.app.util.UserStatusManager;

public class MatchmakingActivity extends AppCompatActivity {

    private MatchRepository matchRepository;
    private String uid;
    private boolean matchFound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matchmaking);

        String inviteMatchId = getIntent().getStringExtra("inviteMatchId");
        boolean inviteIsPlayer1 = getIntent().getBooleanExtra("isPlayer1", true);
        String inviteOpponentUsername = getIntent().getStringExtra("opponentUsername");
        String usernameExtra = getIntent().getStringExtra("username");
        final String username = usernameExtra != null ? usernameExtra : "Player";

        matchRepository = new MatchRepository();
        uid = matchRepository.getUid();

        if (inviteMatchId != null) {
            matchFound = true;
            Runnable launchMatch = () -> {
                UserStatusManager.setInGame(FirebaseAuth.getInstance(), true);
                Intent intent = new Intent(this, MatchActivity.class);
                intent.putExtra("matchId", inviteMatchId);
                intent.putExtra("isPlayer1", inviteIsPlayer1);
                intent.putExtra("username", username);
                intent.putExtra("opponentUsername", inviteOpponentUsername);
                startActivity(intent);
                finish();
            };
            launchMatch.run();
            return;
        }

        MaterialButton btnCancel = findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> {
            matchRepository.leaveQueue(uid);
            finish();
        });

        matchRepository.joinQueue(
            username,
            (matchId, isPlayer1, opponentUsername) -> {
                if (matchFound) return;
                matchFound = true;
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                boolean isGuest = currentUser == null || currentUser.isAnonymous();

                Runnable launchMatch = () -> {
                    UserStatusManager.setInGame(FirebaseAuth.getInstance(), true);

                    Intent intent = new Intent(this, MatchActivity.class);
                    intent.putExtra("matchId",          matchId);
                    intent.putExtra("isPlayer1",         isPlayer1);
                    intent.putExtra("username",          username);
                    intent.putExtra("opponentUsername",  opponentUsername);
                    startActivity(intent);
                    finish();
                };

                if (isGuest) {
                    launchMatch.run();
                } else {
                    matchRepository.deductToken(uid, new RepositoryCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean hasToken) {
                            if (Boolean.TRUE.equals(hasToken)) {
                                launchMatch.run();
                            } else {
                                matchRepository.leaveQueue(uid);
                                Toast.makeText(MatchmakingActivity.this,
                                    "No tokens left. Come back tomorrow!", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        }
                        @Override
                        public void onFailure(Exception e) {
                            launchMatch.run();
                        }
                    });
                }
            },
            new RepositoryCallback<Void>() {
                @Override public void onSuccess(Void v) {}
                @Override public void onFailure(Exception e) {
                    Toast.makeText(MatchmakingActivity.this,
                        "Matchmaking error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!matchFound) {
            matchRepository.leaveQueue(uid);
        }
    }
}
