package com.slagalica.app.ui.game.koznazna;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.slagalica.app.R;
import com.slagalica.app.databinding.ActivityKoZnaZnaBinding;
import com.slagalica.app.model.KoZnaZnaQuestion;
import com.slagalica.app.util.ConfirmDialog;
import com.slagalica.app.viewmodel.KoZnaZnaViewModel;

import java.util.List;

public class KoZnaZnaActivity extends AppCompatActivity {

    private static final long FEEDBACK_DELAY_MS = 1500L;

    private KoZnaZnaViewModel viewModel;
    private ActivityKoZnaZnaBinding binding;

    private boolean isMatchGame;
    private boolean isPlayer1;
    private String opponentUsername;
    private boolean gameFinished = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingAdvance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityKoZnaZnaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        isMatchGame = getIntent().getBooleanExtra("isMatchGame", false);
        isPlayer1 = getIntent().getBooleanExtra("isPlayer1", true);
        String matchId = getIntent().getStringExtra("matchId");

        String yourUsername = getIntent().getStringExtra("username");
        String you = yourUsername == null ? "You" : yourUsername;

        opponentUsername = getIntent().getStringExtra("opponentUsername");
        if (opponentUsername == null) opponentUsername = "Opponent";

        viewModel = new ViewModelProvider(this).get(KoZnaZnaViewModel.class);

        if (isMatchGame && matchId != null)
            viewModel.initMatchMode(matchId, isPlayer1);
        else
            viewModel.loadQuestionsForSolo();

        binding.header.tvGameTitle.setText("Who knows, knows");
        binding.header.btnClose.setOnClickListener(v -> showExitConfirm());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { showExitConfirm(); }
        });

        binding.header.tvPlayerName.setText(you);
        binding.header.tvOpponentName.setText(opponentUsername);

        binding.btnSkip.setOnClickListener(v -> viewModel.submitAnswer(-1, 1));

        setupObservers();
    }

    private void setupObservers() {
        viewModel.getCurrentIndex().observe(this, index -> {
            if (index == null) return;
            cancelPendingAdvance();
            binding.header.tvRound.setText("Question " + (index + 1) + " / 5");
            hideBanner();
            renderQuestion(index);
            viewModel.startQuestion();
        });

        viewModel.getTimeLeft().observe(this, ms -> {
            if (ms == null) return;
            binding.header.tvTimer.setText((long) Math.ceil(ms / 1000.0) + "s");
        });

        viewModel.getMyRunningScore().observe(this, score -> {
            if (score == null) return;
            binding.header.tvScore.setText(String.valueOf(score));
        });

        viewModel.getOpponentRunningScore().observe(this, score -> {
            if (score == null) return;
            try {
                binding.header.tvScoreOpponent.setText(String.valueOf(score));
            } catch (NullPointerException ignored) { }
        });

        viewModel.getQuestionResult().observe(this, result -> {
            if (result == null) return;
            int delta = result[0];
            int correct = result[1];
            int myAnswer = result[2];
            int skipped = result[3];
            int lostToSpeed = result[4];

            highlightAnswers(correct, myAnswer);
            if (lostToSpeed == 1) showSpeedLostBanner();
            else showAnswerFeedback(delta, correct, myAnswer, skipped);

            pendingAdvance = viewModel::advanceOrFinish;
            uiHandler.postDelayed(pendingAdvance, FEEDBACK_DELAY_MS);
        });

        viewModel.getMyScoreReady().observe(this, myScore -> {
            if (myScore == null || gameFinished || !isMatchGame) return;
            gameFinished = true;
            int p1Score = isPlayer1 ? myScore : 0;
            int p2Score = isPlayer1 ? 0 : myScore;
            Intent result = new Intent();
            result.putExtra("p1Score", p1Score);
            result.putExtra("p2Score", p2Score);
            setResult(RESULT_OK, result);
            finish();
        });

        viewModel.getFinalScores().observe(this, scores -> {
            if (scores == null || gameFinished) return;
            gameFinished = true;
            uiHandler.postDelayed(this::finishWithResult, 800);
        });
    }

    private void renderQuestion(int index) {
        List<KoZnaZnaQuestion> qs = viewModel.getQuestions().getValue();
        if (qs == null || index >= qs.size()) return;

        KoZnaZnaQuestion q = qs.get(index);
        String[] labels = {"A", "B", "C", "D"};
        binding.tvQuestion.setText(q.getText());

        MaterialButton[] btns = answerButtons();
        for (int i = 0; i < 4; i++) {
            final int ai = i;
            btns[i].setText(labels[i] + ".  " + q.getAnswers().get(i));
            resetButtonAppearance(btns[i]);
            btns[i].setOnClickListener(v -> viewModel.submitAnswer(ai, 0));
        }
    }

    private void showAnswerFeedback(int delta, int correct, int myAnswer, int skipped) {
        binding.bannerResult.setVisibility(View.VISIBLE);

        if (myAnswer == -1 || skipped == 1) {
            binding.ivResultIcon.setImageResource(skipped == 1 ? R.drawable.ic_skip : R.drawable.ic_hourglass);
            binding.tvResultTitle.setText(skipped == 1 ? "Skipped!" : "Time's up!");
            binding.tvResultSub.setText("0 points");
            binding.tvPointsDelta.setText("0");
            binding.tvPointsDelta.setTextColor(getColor(R.color.text_mute));
        } else if (myAnswer == correct) {
            binding.ivResultIcon.setImageResource(R.drawable.ic_check);
            binding.tvResultTitle.setText("Correct answer!");
            binding.tvResultSub.setText("+" + delta + " points");
            binding.tvPointsDelta.setText("+" + delta);
            binding.tvPointsDelta.setTextColor(getColor(R.color.accent));
        } else {
            binding.ivResultIcon.setImageResource(R.drawable.ic_x);
            binding.tvResultTitle.setText("Wrong answer!");
            binding.tvResultSub.setText(delta + " points");
            binding.tvPointsDelta.setText(String.valueOf(delta));
            binding.tvPointsDelta.setTextColor(getColor(R.color.danger));
        }
    }

    private void showSpeedLostBanner() {
        binding.bannerResult.setVisibility(View.VISIBLE);
        binding.ivResultIcon.setImageResource(R.drawable.ic_check);
        binding.tvResultTitle.setText("Correct answer!");
        binding.tvResultSub.setText(opponentUsername + " was faster - no points awarded");
        binding.tvPointsDelta.setText("0");
        binding.tvPointsDelta.setTextColor(getColor(R.color.text_mute));
    }

    private void hideBanner() {
        binding.bannerResult.setVisibility(View.GONE);
    }

    private void highlightAnswers(int correct, int myAnswer) {
        MaterialButton[] btns = answerButtons();
        for (int i = 0; i < 4; i++) {
            if (i == correct)  btns[i].setStrokeColor(getColorStateList(R.color.success));
            else if (i == myAnswer) btns[i].setStrokeColor(getColorStateList(R.color.danger));
        }
    }

    private void resetButtonAppearance(MaterialButton btn) {
        btn.setStrokeColor(getColorStateList(R.color.border));
    }

    private MaterialButton[] answerButtons() {
        return new MaterialButton[]{ binding.btnAnswerA, binding.btnAnswerB, binding.btnAnswerC, binding.btnAnswerD };
    }

    private void finishWithResult() {
        if (!isMatchGame) { finish(); return; }
        int[] scores = viewModel.getFinalScores().getValue();
        int p1 = scores != null ? scores[0] : 0;
        int p2 = scores != null ? scores[1] : 0;
        Intent result = new Intent();
        result.putExtra("p1Score", p1);
        result.putExtra("p2Score", p2);
        setResult(RESULT_OK, result);
        finish();
    }

    private void showExitConfirm() {
        ConfirmDialog.show(this, "Quit game?", "Your progress will be lost.", "Quit", "Keep playing",
                () -> {
                    viewModel.writeForfeit();
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("quitMatch", true);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                });
    }

    private void cancelPendingAdvance() {
        if (pendingAdvance != null) {
            uiHandler.removeCallbacks(pendingAdvance);
            pendingAdvance = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        if (!gameFinished) viewModel.writeForfeit();
    }
}