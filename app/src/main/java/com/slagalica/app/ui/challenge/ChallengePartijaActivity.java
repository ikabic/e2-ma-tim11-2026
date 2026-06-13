package com.slagalica.app.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.slagalica.app.R;
import com.slagalica.app.ui.game.asocijacije.AsocijacijeActivity;
import com.slagalica.app.ui.game.korakpokorak.KorakPoKorakActivity;
import com.slagalica.app.ui.game.koznazna.KoZnaZnaActivity;
import com.slagalica.app.ui.game.mojbroj.MojBrojActivity;
import com.slagalica.app.ui.game.skocko.SkockoActivity;
import com.slagalica.app.ui.game.spojnice.SpojniceActivity;
import com.slagalica.app.viewmodel.ChallengeViewModel;

public class ChallengePartijaActivity extends AppCompatActivity {

    private static final Class<?>[] GAME_CLASSES = {
            KoZnaZnaActivity.class,
            SpojniceActivity.class,
            AsocijacijeActivity.class,
            SkockoActivity.class,
            KorakPoKorakActivity.class,
            MojBrojActivity.class
    };
    private static final String[] GAME_NAMES = {
            "Ko zna zna", "Spojnice", "Asocijacije", "Skocko", "Korak po korak", "Moj broj"
    };

    private ChallengeViewModel viewModel;
    private ActivityResultLauncher<Intent> gameLauncher;

    private String challengeId, myUsername, soloMatchId;
    private int currentGame = 0;
    private int totalScore = 0;
    private boolean submitted = false;

    private TextView tvProgress, tvGameName, tvScore;
    private Button btnPlayGame;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_partija);

        challengeId = getIntent().getStringExtra("challengeId");
        myUsername = getIntent().getStringExtra("username");

        viewModel = new ViewModelProvider(this).get(ChallengeViewModel.class);
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anon";
        soloMatchId = "challenge_" + challengeId + "_" + uid;

        tvProgress = findViewById(R.id.tvProgress);
        tvGameName = findViewById(R.id.tvGameName);
        tvScore = findViewById(R.id.tvScore);
        btnPlayGame = findViewById(R.id.btnPlayGame);

        gameLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    int p1 = result.getData() != null
                            ? result.getData().getIntExtra("p1Score", 0) : 0;
                    totalScore += p1;
                    currentGame++;
                    showCurrentOrFinish();
                });

        showCurrentOrFinish();
    }

    private void showCurrentOrFinish() {
        tvScore.setText(String.valueOf(totalScore));
        if (currentGame >= GAME_CLASSES.length) {
            finishPartija();
            return;
        }
        tvProgress.setText("Game " + (currentGame + 1) + " / " + GAME_CLASSES.length);
        tvGameName.setText(GAME_NAMES[currentGame]);
        btnPlayGame.setText(currentGame == 0 ? "Start" : "Next game");
        btnPlayGame.setEnabled(true);
        btnPlayGame.setOnClickListener(v -> launchGame(currentGame));
    }

    private void launchGame(int idx) {
        btnPlayGame.setEnabled(false);
        Intent intent = new Intent(this, GAME_CLASSES[idx]);
        intent.putExtra("isMatchGame", true);
        intent.putExtra("isPlayer1", true);
        intent.putExtra("matchId", soloMatchId);
        intent.putExtra("username", myUsername);
        intent.putExtra("opponentUsername", "");
        intent.putExtra("prevTotalP1", 0);
        intent.putExtra("prevTotalP2", 0);
        intent.putExtra("soloContinue", true);
        gameLauncher.launch(intent);
    }

    private void finishPartija() {
        if (submitted) return;
        submitted = true;
        tvProgress.setText("Done");
        tvGameName.setText("Partija complete");
        btnPlayGame.setEnabled(false);
        btnPlayGame.setText("Submitting...");
        viewModel.submitScore(challengeId, totalScore, () -> {
            viewModel.resolveIfReady(challengeId);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        if (submitted || currentGame >= GAME_CLASSES.length) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Leave partija?")
                .setMessage("If you leave now, your run will not be submitted.")
                .setPositiveButton("Leave", (d, w) -> finish())
                .setNegativeButton("Stay", null)
                .show();
    }
}
