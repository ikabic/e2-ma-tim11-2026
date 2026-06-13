package com.slagalica.app.ui.game.korakpokorak;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.slagalica.app.util.ConfirmDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.slagalica.app.R;
import com.slagalica.app.model.KorakPoKorakQuestion;
import com.slagalica.app.util.GameToast;
import com.slagalica.app.viewmodel.KorakPoKorakViewModel;

import java.util.List;

public class KorakPoKorakActivity extends AppCompatActivity {

    private static final int STEP_DURATION_MS  = 10000;
    private static final int BONUS_DURATION_MS = 10000;
    private static final int MAX_STEPS         = 7;

    private TextView tvRound, tvTimer, tvScore, tvScoreOpponent, tvStep;
    private TextView[] clueViews;
    private View[] cardViews;
    private View[] progressViews;
    private TextInputEditText etAnswer;
    private MaterialButton btnSubmit;
    private LinearLayout panelPlayerYou, panelPlayerOpponent;
    private LinearLayout sectionGame, sectionGameOver;
    private LinearLayout panelFinalP1, panelFinalP2;
    private TextView tvFinalScoreP1, tvFinalScoreP2, tvWinner;
    private TextView tvFinalNameP1, tvFinalNameP2;
    private String playerUsername   = "You";
    private String opponentUsername = "Opponent";

    private KorakPoKorakViewModel viewModel;
    private CountDownTimer stepTimer;
    private KorakPoKorakQuestion currentQuestion;
    private boolean isMatchGame;
    private boolean isPlayer1;
    private boolean isSoloSteal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_korak_po_korak);

        playerUsername = getIntent().getStringExtra("username");
        if (playerUsername == null) playerUsername = "You";
        String oppName = getIntent().getStringExtra("opponentUsername");
        if (oppName != null) opponentUsername = oppName;
        isMatchGame = getIntent().getBooleanExtra("isMatchGame", false);
        isPlayer1   = getIntent().getBooleanExtra("isPlayer1", true);
        isSoloSteal = getIntent().getBooleanExtra("kpkSoloSteal", false);
        initViews();
        GameToast.showCountdown(this, () -> {
            setupViewModel();
            startGameOrSteal();
        });
    }

    private void initViews() {
        tvRound = findViewById(R.id.tvRound);
        ((TextView) findViewById(R.id.tvGameTitle)).setText("Step by step");
        tvTimer = findViewById(R.id.tvTimer);
        tvScore = findViewById(R.id.tvScore);
        tvScoreOpponent = findViewById(R.id.tvScoreOpponent);
        tvStep = findViewById(R.id.tvStep);
        etAnswer = findViewById(R.id.etAnswer);
        btnSubmit = findViewById(R.id.btnSubmit);

        clueViews = new TextView[]{
            findViewById(R.id.tvClue1), findViewById(R.id.tvClue2),
            findViewById(R.id.tvClue3), findViewById(R.id.tvClue4),
            findViewById(R.id.tvClue5), findViewById(R.id.tvClue6),
            findViewById(R.id.tvClue7)
        };

        cardViews = new View[]{
            findViewById(R.id.cardClue1), findViewById(R.id.cardClue2),
            findViewById(R.id.cardClue3), findViewById(R.id.cardClue4),
            findViewById(R.id.cardClue5), findViewById(R.id.cardClue6),
            findViewById(R.id.cardClue7)
        };

        progressViews = new View[]{
            findViewById(R.id.progressStep1), findViewById(R.id.progressStep2),
            findViewById(R.id.progressStep3), findViewById(R.id.progressStep4),
            findViewById(R.id.progressStep5), findViewById(R.id.progressStep6),
            findViewById(R.id.progressStep7)
        };

        panelPlayerYou = findViewById(R.id.panelPlayerYou);
        panelPlayerOpponent = findViewById(R.id.panelPlayerOpponent);
        sectionGame = findViewById(R.id.sectionGame);
        sectionGameOver = findViewById(R.id.sectionGameOver);
        panelFinalP1 = findViewById(R.id.panelFinalP1);
        panelFinalP2 = findViewById(R.id.panelFinalP2);
        tvFinalNameP1 = findViewById(R.id.tvFinalNameP1);
        tvFinalNameP2 = findViewById(R.id.tvFinalNameP2);
        tvFinalScoreP1 = findViewById(R.id.tvFinalScoreP1);
        tvFinalScoreP2 = findViewById(R.id.tvFinalScoreP2);
        tvWinner = findViewById(R.id.tvWinner);

        findViewById(R.id.btnClose).setOnClickListener(v -> showExitConfirm());
        btnSubmit.setOnClickListener(v -> submitAnswer());
        etAnswer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitAnswer();
                return true;
            }
            return false;
        });
    }

    private void startGameOrSteal() {
        String stealQId = getIntent().getStringExtra("kpkStealQuestionId");
        if (isSoloSteal && stealQId != null) {
            viewModel.startSoloSteal(stealQId);
        } else if (stealQId != null && isMatchGame && !isPlayer1) {
            viewModel.loadQuestionByIdForSteal(stealQId);
        } else {
            viewModel.loadQuestion();
        }
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(KorakPoKorakViewModel.class);

        if (isMatchGame) {
            String matchId = getIntent().getStringExtra("matchId");
            viewModel.initMatchMode(matchId, isPlayer1);
        }

        viewModel.getQuestion().observe(this, this::displayQuestion);

        viewModel.getCurrentStep().observe(this, step -> {
            if (viewModel.getGameState().getValue() == KorakPoKorakViewModel.GameState.STEAL_ATTEMPT) return;
            tvStep.setText("Step " + (step + 1) + "/7");
            updateClueVisibility(step);
            startStepTimer(step);
        });

        viewModel.getCurrentRound().observe(this, round ->
            tvRound.setText("Round " + round + "/2")
        );

        viewModel.getPlayer1Score().observe(this, s -> tvScore.setText(String.valueOf(s)));
        viewModel.getPlayer2Score().observe(this, s -> tvScoreOpponent.setText(String.valueOf(s)));

        viewModel.getGameState().observe(this, state -> {
            if (isMatchGame && !isPlayer1
                    && state == KorakPoKorakViewModel.GameState.PLAYER_TURN
                    && Integer.valueOf(1).equals(viewModel.getCurrentRound().getValue())) {
                viewModel.skipToRound2();
                return;
            }

            switch (state) {
                case LOADING:
                    btnSubmit.setEnabled(false);
                    break;
                case PLAYER_TURN:
                    btnSubmit.setEnabled(true);
                    highlightPlayer(isMatchGame || viewModel.getCurrentRound().getValue() == 1);
                    break;
                case OPPONENT_BONUS:
                    if (stepTimer != null) stepTimer.cancel();
                    if (isMatchGame) {
                        String qId = currentQuestion != null ? currentQuestion.getId() : null;
                        Intent r = new Intent();
                        if (isPlayer1) {
                            int p1s = safeScore(viewModel.getPlayer1Score().getValue());
                            r.putExtra("p1Score", p1s);
                            r.putExtra("p2Score", 0);
                        } else {
                            int p2s = safeScore(viewModel.getPlayer2Score().getValue());
                            r.putExtra("p1Score", 0);
                            r.putExtra("p2Score", p2s);
                        }
                        if (qId != null) r.putExtra("kpkStealQuestionId", qId);
                        setResult(RESULT_OK, r);
                        finish();
                        return;
                    }
                    highlightPlayer(viewModel.getCurrentRound().getValue() != 1);
                    GameToast.show(this, "Opponent has 10s to steal!", GameToast.Type.INFO);
                    startBonusTimer();
                    break;

                case STEAL_ATTEMPT:
                    if (stepTimer != null) stepTimer.cancel();
                    KorakPoKorakQuestion sq = viewModel.getStealQuestion().getValue();
                    if (sq != null) {
                        currentQuestion = sq;
                        updateClueVisibility(MAX_STEPS);
                    }
                    tvStep.setText("Steal attempt!");
                    btnSubmit.setEnabled(true);
                    highlightPlayer(true);
                    GameToast.show(this, "P1 failed! 10 seconds to steal +5 pts.", GameToast.Type.INFO);
                    startStealTimer();
                    break;

                case SOLO_STEAL:
                    if (stepTimer != null) stepTimer.cancel();
                    KorakPoKorakQuestion sqSolo = viewModel.getStealQuestion().getValue();
                    if (sqSolo != null) {
                        currentQuestion = sqSolo;
                        updateClueVisibility(MAX_STEPS);
                    }
                    tvStep.setText("Steal attempt!");
                    btnSubmit.setEnabled(true);
                    highlightPlayer(true);
                    GameToast.show(this, "Opponent failed! 10 seconds to steal +5 pts.", GameToast.Type.INFO);
                    startSoloStealTimer();
                    break;

                case SOLO_STEAL_DONE: {
                    if (stepTimer != null) stepTimer.cancel();
                    Integer bonus = viewModel.getSoloStealBonus().getValue();
                    Intent soloResult = new Intent();
                    soloResult.putExtra("kpkSoloStealBonus", bonus != null ? bonus : 0);
                    setResult(RESULT_OK, soloResult);
                    finish();
                    return;
                }
                case ROUND_OVER:
                    if (stepTimer != null) stepTimer.cancel();
                    if (isMatchGame && isPlayer1) {
                        int p1 = safeScore(viewModel.getPlayer1Score().getValue());
                        Intent r = new Intent();
                        r.putExtra("p1Score", p1);
                        r.putExtra("p2Score", 0);
                        setResult(RESULT_OK, r);
                        finish();
                        return;
                    }
                    viewModel.startNextRound();
                    break;
                case GAME_OVER:
                    if (stepTimer != null) stepTimer.cancel();
                    int p1 = safeScore(viewModel.getPlayer1Score().getValue());
                    int p2 = safeScore(viewModel.getPlayer2Score().getValue());
                    if (isMatchGame) {
                        Intent matchResult = new Intent();
                        matchResult.putExtra("p1Score", p1);
                        matchResult.putExtra("p2Score", p2);
                        setResult(RESULT_OK, matchResult);
                        finish();
                        return;
                    }
                    tvFinalNameP1.setText(playerUsername);
                    tvFinalNameP2.setText(opponentUsername);
                    tvFinalScoreP1.setText(String.valueOf(p1));
                    tvFinalScoreP2.setText(String.valueOf(p2));
                    if (p1 > p2) {
                        tvWinner.setText(playerUsername + " wins!");
                        panelFinalP1.setBackgroundResource(R.drawable.bg_player_you);
                        panelFinalP2.setBackgroundResource(R.drawable.bg_player_other);
                    } else if (p2 > p1) {
                        tvWinner.setText(opponentUsername + " wins!");
                        panelFinalP1.setBackgroundResource(R.drawable.bg_player_other);
                        panelFinalP2.setBackgroundResource(R.drawable.bg_player_you);
                    } else {
                        tvWinner.setText("Draw!");
                        panelFinalP1.setBackgroundResource(R.drawable.bg_player_other);
                        panelFinalP2.setBackgroundResource(R.drawable.bg_player_other);
                    }
                    showGameOver();
                    break;
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) GameToast.show(this, error, GameToast.Type.ERROR);
        });
    }

    private void displayQuestion(KorakPoKorakQuestion question) {
        currentQuestion = question;
        Integer step = viewModel.getCurrentStep().getValue();
        updateClueVisibility(step != null ? step : 0);
    }

    private void updateClueVisibility(int currentStep) {
        List<String> clues = (currentQuestion != null) ? currentQuestion.getClues() : null;
        for (int i = 0; i < clueViews.length; i++) {
            TextView badge = (TextView) ((ViewGroup) cardViews[i]).getChildAt(0);
            badge.setBackgroundResource(R.drawable.bg_badge_step);
            badge.setTextColor(getResources().getColor(R.color.accent_ink, null));
            if (i == currentStep) {
                cardViews[i].setBackgroundResource(R.drawable.bg_clue_active);
                cardViews[i].setAlpha(1f);
                if (clues != null && i < clues.size()) clueViews[i].setText(clues.get(i));
                clueViews[i].setTextColor(getResources().getColor(R.color.text, null));
            } else if (i < currentStep) {
                cardViews[i].setBackgroundResource(R.drawable.bg_clue_normal);
                cardViews[i].setAlpha(1f);
                if (clues != null && i < clues.size()) clueViews[i].setText(clues.get(i));
                clueViews[i].setTextColor(getResources().getColor(R.color.text_mute, null));
            } else {
                cardViews[i].setBackgroundResource(R.drawable.bg_clue_locked);
                cardViews[i].setAlpha(0.4f);
                clueViews[i].setText("Reveals in " + ((i - currentStep) * 10) + "s");
                clueViews[i].setTextColor(getResources().getColor(R.color.text_dim, null));
            }
            progressViews[i].setBackgroundResource(
                i <= currentStep ? R.drawable.bg_clue_active : R.drawable.bg_input
            );
        }
    }

    private void updateLockedCountdowns(long msRemaining, int currentStep) {
        long secsToNext = (msRemaining + 999) / 1000;
        for (int i = currentStep + 1; i < clueViews.length; i++) {
            long secs = secsToNext + (long)(i - currentStep - 1) * 10;
            clueViews[i].setText("Reveals in " + secs + "s");
        }
    }

    private void startStepTimer(int step) {
        if (stepTimer != null) stepTimer.cancel();
        stepTimer = new CountDownTimer(STEP_DURATION_MS, 1000) {
            @Override
            public void onTick(long ms) {
                tvTimer.setText(ms / 1000 + "s");
                updateLockedCountdowns(ms, step);
            }

            @Override
            public void onFinish() { viewModel.onStepTimeout(); }
        }.start();
    }

    private void startBonusTimer() {
        if (stepTimer != null) stepTimer.cancel();
        stepTimer = new CountDownTimer(BONUS_DURATION_MS, 1000) {
            @Override
            public void onTick(long ms) { tvTimer.setText(ms / 1000 + "s"); }

            @Override
            public void onFinish() { viewModel.submitBonusAnswer(""); }
        }.start();
    }

    private void startStealTimer() {
        if (stepTimer != null) stepTimer.cancel();
        stepTimer = new CountDownTimer(BONUS_DURATION_MS, 1000) {
            @Override
            public void onTick(long ms) { tvTimer.setText(ms / 1000 + "s"); }

            @Override
            public void onFinish() {
                viewModel.submitStealAttempt("");
                etAnswer.setText("");
            }
        }.start();
    }

    private void startSoloStealTimer() {
        if (stepTimer != null) stepTimer.cancel();
        stepTimer = new CountDownTimer(BONUS_DURATION_MS, 1000) {
            @Override
            public void onTick(long ms) { tvTimer.setText(ms / 1000 + "s"); }

            @Override
            public void onFinish() {
                viewModel.submitSoloSteal("");
                etAnswer.setText("");
            }
        }.start();
    }

    private void submitAnswer() {
        if (viewModel == null) return;
        String answer = etAnswer.getText().toString().trim();
        KorakPoKorakViewModel.GameState state = viewModel.getGameState().getValue();

        if (state == KorakPoKorakViewModel.GameState.PLAYER_TURN) {
            if (answer.isEmpty()) return;
            if (viewModel.submitAnswer(answer)) {
                if (stepTimer != null) stepTimer.cancel();
                GameToast.show(this, "Correct!", GameToast.Type.SUCCESS);
            } else {
                GameToast.show(this, "Wrong answer, try again!", GameToast.Type.ERROR);
                return;
            }
        } else if (state == KorakPoKorakViewModel.GameState.OPPONENT_BONUS) {
            if (stepTimer != null) { stepTimer.cancel(); stepTimer = null; }
            viewModel.submitBonusAnswer(answer);
        } else if (state == KorakPoKorakViewModel.GameState.STEAL_ATTEMPT) {
            if (stepTimer != null) { stepTimer.cancel(); stepTimer = null; }
            boolean correct = !answer.isEmpty();
            viewModel.submitStealAttempt(answer);
            if (correct) GameToast.show(this, "Steal attempt submitted!", GameToast.Type.INFO);
        } else if (state == KorakPoKorakViewModel.GameState.SOLO_STEAL) {
            if (stepTimer != null) { stepTimer.cancel(); stepTimer = null; }
            viewModel.submitSoloSteal(answer);
        }
        etAnswer.setText("");
    }

    private void showGameOver() {
        sectionGame.setVisibility(View.GONE);
        sectionGameOver.setVisibility(View.VISIBLE);
    }

    private int safeScore(Integer v) {
        return v == null ? 0 : v;
    }

    private void highlightPlayer(boolean youActive) {
        panelPlayerYou.setBackgroundResource(
            youActive ? R.drawable.bg_player_you : R.drawable.bg_player_other);
        panelPlayerOpponent.setBackgroundResource(
            youActive ? R.drawable.bg_player_other : R.drawable.bg_player_you);
    }

    private void showExitConfirm() {
        ConfirmDialog.show(this, "Quit game?", "Your progress will be lost.",
            "Quit", "Keep playing", this::quitGame);
    }

    private void quitGame() {
        if (isMatchGame) {
            Intent result = new Intent();
            result.putExtra("quitMatch", true);
            setResult(RESULT_OK, result);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        showExitConfirm();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stepTimer != null) stepTimer.cancel();
    }
}
