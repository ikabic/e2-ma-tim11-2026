package com.slagalica.app.ui.game.skocko;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.slagalica.app.R;
import com.slagalica.app.repository.AsocijacijeRepository;
import com.slagalica.app.repository.SkockoRepository;
import com.slagalica.app.ui.HomeActivity;
import com.slagalica.app.viewmodel.SkockoViewModel;
import com.slagalica.app.viewmodel.SkockoViewModel.GuessEntry;
import com.slagalica.app.viewmodel.SkockoViewModel.GuessResult;

import java.util.ArrayList;
import java.util.List;


public class SkockoActivity extends AppCompatActivity {

    private static final long ROUND_DURATION_MS  = 30_000L;
    private static final long BONUS_DURATION_MS  = 10_000L;
    private TextView tvRound, tvTimer, tvScore, tvScoreOpponent, tvActivePlayer, tvAttempt, tvFeedback;

    private View[]     guessRows;
    private TextView[] symViews;
    private View[]     dotViews;
    private TextView[] guessSlots;
    private MaterialButton btnSubmitGuess, btnDelete;
    private View     sectionGameOver;
    private View     nestedScrollView;
    private TextView tvFinalScoreP1, tvFinalScoreP2, tvWinner;
    private final List<Integer> currentGuess = new ArrayList<>();

    private SkockoViewModel viewModel;
    private CountDownTimer  activeTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_skocko);

        initViews();
        setupViewModel();
        viewModel.loadQuestion();
        new SkockoRepository().seedTestData();
    }

    private void initViews() {
        tvRound         = findViewById(R.id.tvRound);
        tvTimer         = findViewById(R.id.tvTimer);
        tvScore         = findViewById(R.id.tvScore);
        tvScoreOpponent = findViewById(R.id.tvScoreOpponent);
        tvActivePlayer  = findViewById(R.id.tvActivePlayer);
        tvAttempt       = findViewById(R.id.tvAttempt);
        tvFeedback      = findViewById(R.id.tvFeedback);

        nestedScrollView = findViewById(R.id.nestedScrollView);
        sectionGameOver  = findViewById(R.id.sectionGameOver);
        tvFinalScoreP1   = findViewById(R.id.tvFinalScoreP1);
        tvFinalScoreP2   = findViewById(R.id.tvFinalScoreP2);
        tvWinner         = findViewById(R.id.tvWinner);

        guessRows = new View[]{
                findViewById(R.id.rowGuess1), findViewById(R.id.rowGuess2),
                findViewById(R.id.rowGuess3), findViewById(R.id.rowGuess4),
                findViewById(R.id.rowGuess5), findViewById(R.id.rowGuess6)
        };

        int[] symIds = {
                R.id.sym1_0, R.id.sym1_1, R.id.sym1_2, R.id.sym1_3,
                R.id.sym2_0, R.id.sym2_1, R.id.sym2_2, R.id.sym2_3,
                R.id.sym3_0, R.id.sym3_1, R.id.sym3_2, R.id.sym3_3,
                R.id.sym4_0, R.id.sym4_1, R.id.sym4_2, R.id.sym4_3,
                R.id.sym5_0, R.id.sym5_1, R.id.sym5_2, R.id.sym5_3,
                R.id.sym6_0, R.id.sym6_1, R.id.sym6_2, R.id.sym6_3,
        };
        symViews = new TextView[symIds.length];
        for (int i = 0; i < symIds.length; i++) symViews[i] = findViewById(symIds[i]);

        int[] dotIds = {
                R.id.dot1_0, R.id.dot1_1, R.id.dot1_2, R.id.dot1_3,
                R.id.dot2_0, R.id.dot2_1, R.id.dot2_2, R.id.dot2_3,
                R.id.dot3_0, R.id.dot3_1, R.id.dot3_2, R.id.dot3_3,
                R.id.dot4_0, R.id.dot4_1, R.id.dot4_2, R.id.dot4_3,
                R.id.dot5_0, R.id.dot5_1, R.id.dot5_2, R.id.dot5_3,
                R.id.dot6_0, R.id.dot6_1, R.id.dot6_2, R.id.dot6_3,
        };
        dotViews = new View[dotIds.length];
        for (int i = 0; i < dotIds.length; i++) dotViews[i] = findViewById(dotIds[i]);

        guessSlots = new TextView[]{
                findViewById(R.id.tvGuess0), findViewById(R.id.tvGuess1),
                findViewById(R.id.tvGuess2), findViewById(R.id.tvGuess3)
        };

        int[] symBtnIds = {
                R.id.btnSym0, R.id.btnSym1, R.id.btnSym2,
                R.id.btnSym3, R.id.btnSym4, R.id.btnSym5
        };
        for (int i = 0; i < symBtnIds.length; i++) {
            final int symIdx = i;
            findViewById(symBtnIds[i]).setOnClickListener(v -> addSymbol(symIdx));
        }

        btnDelete       = findViewById(R.id.btnDelete);
        btnSubmitGuess  = findViewById(R.id.btnSubmitGuess);

        btnDelete.setOnClickListener(v -> removeLastSymbol());
        btnSubmitGuess.setOnClickListener(v -> submitGuess());

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnGoHome).setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(SkockoViewModel.class);

        viewModel.getCurrentRound().observe(this, r ->
                tvRound.setText("ROUND " + r + " / 2"));

        viewModel.getPlayer1Score().observe(this, s -> tvScore.setText(String.valueOf(s)));
        viewModel.getPlayer2Score().observe(this, s -> tvScoreOpponent.setText(String.valueOf(s)));

        viewModel.getActivePlayer().observe(this, ap ->
                tvActivePlayer.setText("Player " + ap + "'s turn"));

        viewModel.getCurrentAttempt().observe(this, attempt ->
                tvAttempt.setText("Attempt " + attempt + " / 6"));

        viewModel.getFeedbackMsg().observe(this, msg -> {
            if (msg != null) tvFeedback.setText(msg);
        });

        viewModel.getGuessHistory().observe(this, this::renderGuessHistory);

        viewModel.getGameState().observe(this, state -> {
            switch (state) {
                case LOADING:
                    setInputEnabled(false);
                    break;
                case PLAYER_TURN:
                    setInputEnabled(true);
                    startRoundTimer();
                    break;
                case BONUS_TURN:
                    cancelTimer();
                    setInputEnabled(true);
                    Toast.makeText(this, "Bonus! 10 seconds to steal!", Toast.LENGTH_SHORT).show();
                    startBonusTimer();
                    break;
                case ROUND_OVER:
                    cancelTimer();
                    setInputEnabled(false);
                    resetBoardUI();
                    viewModel.startNextRound();
                    break;
                case GAME_OVER:
                    cancelTimer();
                    setInputEnabled(false);
                    showGameOver();
                    break;
            }
        });

        viewModel.getErrorMessage().observe(this, err -> {
            if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
        });
    }

    private void addSymbol(int symIdx) {
        if (currentGuess.size() >= SkockoViewModel.CODE_LENGTH) return;
        currentGuess.add(symIdx);
        refreshGuessSlots();
        btnSubmitGuess.setEnabled(currentGuess.size() == SkockoViewModel.CODE_LENGTH);
    }

    private void removeLastSymbol() {
        if (currentGuess.isEmpty()) return;
        currentGuess.remove(currentGuess.size() - 1);
        refreshGuessSlots();
        btnSubmitGuess.setEnabled(false);
    }

    private void refreshGuessSlots() {
        for (int i = 0; i < SkockoViewModel.CODE_LENGTH; i++) {
            if (i < currentGuess.size()) {
                guessSlots[i].setText(SkockoViewModel.SYMBOLS[currentGuess.get(i)]);
                guessSlots[i].setBackgroundResource(R.drawable.bg_clue_active);
            } else {
                guessSlots[i].setText("·");
                guessSlots[i].setBackgroundResource(R.drawable.bg_input);
            }
        }
    }

    private void submitGuess() {
        if (currentGuess.size() != SkockoViewModel.CODE_LENGTH) return;

        SkockoViewModel.GameState state = viewModel.getGameState().getValue();
        if (state == SkockoViewModel.GameState.PLAYER_TURN) {
            viewModel.submitGuess(new ArrayList<>(currentGuess));
        } else if (state == SkockoViewModel.GameState.BONUS_TURN) {
            cancelTimer();
            viewModel.submitBonusGuess(new ArrayList<>(currentGuess));
        }

        currentGuess.clear();
        refreshGuessSlots();
        btnSubmitGuess.setEnabled(false);
    }

    private void renderGuessHistory(List<GuessEntry> history) {
        for (int rowIdx = 0; rowIdx < 6; rowIdx++) {
            if (rowIdx < history.size()) {
                guessRows[rowIdx].setVisibility(View.VISIBLE);
                GuessEntry entry = history.get(rowIdx);
                for (int pos = 0; pos < 4; pos++) {
                    int flatSym = rowIdx * 4 + pos;
                    symViews[flatSym].setText(SkockoViewModel.SYMBOLS[entry.symbols.get(pos)]);
                    colorDot(dotViews[flatSym], entry.results[pos]);
                }
            } else {
                guessRows[rowIdx].setVisibility(View.INVISIBLE);
            }
        }
    }

    private void colorDot(View dot, GuessResult result) {
        int color;
        switch (result) {
            case CORRECT: color = getResources().getColor(R.color.success, null); break;
            case PRESENT: color = 0xFFF5C842;
                break;
            default:      color = getResources().getColor(R.color.border, null); break;
        }
        dot.setBackgroundColor(color);
    }

    private void startRoundTimer() {
        cancelTimer();
        activeTimer = new CountDownTimer(ROUND_DURATION_MS, 1000) {
            @Override public void onTick(long ms) {
                tvTimer.setText(ms / 1000 + "s");
            }
            @Override public void onFinish() {
                tvTimer.setText("0s");
                viewModel.submitGuess(new ArrayList<>());
            }
        }.start();
    }

    private void startBonusTimer() {
        cancelTimer();
        activeTimer = new CountDownTimer(BONUS_DURATION_MS, 1000) {
            @Override public void onTick(long ms) { tvTimer.setText(ms / 1000 + "s"); }
            @Override public void onFinish() {
                tvTimer.setText("0s");
                viewModel.submitBonusGuess(null);
            }
        }.start();
    }

    private void cancelTimer() {
        if (activeTimer != null) { activeTimer.cancel(); activeTimer = null; }
    }

    private void setInputEnabled(boolean enabled) {
        int[] symBtnIds = {
                R.id.btnSym0, R.id.btnSym1, R.id.btnSym2,
                R.id.btnSym3, R.id.btnSym4, R.id.btnSym5
        };
        for (int id : symBtnIds) findViewById(id).setEnabled(enabled);
        btnDelete.setEnabled(enabled);
        btnSubmitGuess.setEnabled(false);
    }

    private void resetBoardUI() {
        for (View row : guessRows) row.setVisibility(View.INVISIBLE);
        for (TextView slot : guessSlots) { slot.setText("·"); slot.setBackgroundResource(R.drawable.bg_input); }
        currentGuess.clear();
        tvFeedback.setText("");
    }

    private void showGameOver() {
        nestedScrollView.setVisibility(View.GONE);
        sectionGameOver.setVisibility(View.VISIBLE);

        int p1 = viewModel.getPlayer1Score().getValue() != null ? viewModel.getPlayer1Score().getValue() : 0;
        int p2 = viewModel.getPlayer2Score().getValue() != null ? viewModel.getPlayer2Score().getValue() : 0;
        tvFinalScoreP1.setText(String.valueOf(p1));
        tvFinalScoreP2.setText(String.valueOf(p2));

        String result = p1 > p2 ? "Player 1 wins!"
                : p2 > p1 ? "Player 2 wins!"
                : "It's a draw!";
        tvWinner.setText(result);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimer();
    }
}