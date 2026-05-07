package com.slagalica.app.ui.game.asocijacije;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.widget.NestedScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.slagalica.app.R;
import com.slagalica.app.model.AsocijacijeQuestion;
import com.slagalica.app.repository.AsocijacijeRepository;
import com.slagalica.app.ui.HomeActivity;
import com.slagalica.app.viewmodel.AsocijacijeViewModel;

import java.util.List;

public class AsocijacijeActivity extends AppCompatActivity {

    private static final long ROUND_DURATION_MS = 2 * 60 * 1000L;

    private TextView tvRound, tvTimer, tvScore, tvScoreOpponent, tvActivePlayer, tvFeedback;

    private MaterialButton[][] fieldButtons = new MaterialButton[4][4];
    private MaterialButton[]   answerButtons = new MaterialButton[4];
    private MaterialButton     btnFinalAnswer;

    private TextInputEditText etGuess;
    private MaterialButton    btnGuessColumn, btnGuessFinal;
    private LinearLayout      layoutColumnSelector;
    private MaterialButton[]  colSelectors = new MaterialButton[4]; // A B C D

    private View        sectionGameOver;
    private LinearLayout mainScrollSection;
    private TextView    tvFinalScoreP1, tvFinalScoreP2, tvWinner;
    private MaterialButton btnGoHome;

    private int selectedColumn = 0;
    private CountDownTimer roundTimer;
    private AsocijacijeViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);

        initViews();
        setupViewModel();
        viewModel.loadQuestion();
        new AsocijacijeRepository().seedTestData();
    }

    private void initViews() {
        tvRound        = findViewById(R.id.tvRound);
        tvTimer        = findViewById(R.id.tvTimer);
        tvScore        = findViewById(R.id.tvScore);
        tvScoreOpponent= findViewById(R.id.tvScoreOpponent);
        tvActivePlayer = findViewById(R.id.tvActivePlayer);
        tvFeedback     = findViewById(R.id.tvFeedback);

        int[][] fieldIds = {
                { R.id.btnFieldA0, R.id.btnFieldA1, R.id.btnFieldA2, R.id.btnFieldA3 },
                { R.id.btnFieldB0, R.id.btnFieldB1, R.id.btnFieldB2, R.id.btnFieldB3 },
                { R.id.btnFieldC0, R.id.btnFieldC1, R.id.btnFieldC2, R.id.btnFieldC3 },
                { R.id.btnFieldD0, R.id.btnFieldD1, R.id.btnFieldD2, R.id.btnFieldD3 },
        };
        for (int col = 0; col < 4; col++) {
            for (int field = 0; field < 4; field++) {
                fieldButtons[col][field] = findViewById(fieldIds[col][field]);
                final int c = col, f = field;
                fieldButtons[col][field].setOnClickListener(v -> onFieldClicked(c, f));
            }
        }

        int[] answerIds = { R.id.btnAnswerA, R.id.btnAnswerB, R.id.btnAnswerC, R.id.btnAnswerD };
        for (int col = 0; col < 4; col++) {
            answerButtons[col] = findViewById(answerIds[col]);
            final int c = col;
            answerButtons[col].setOnClickListener(v -> selectColumnForGuess(c));
        }

        btnFinalAnswer     = findViewById(R.id.btnFinalAnswer);
        btnFinalAnswer.setOnClickListener(v -> switchToFinalGuessMode());

        etGuess            = findViewById(R.id.etGuess);
        btnGuessColumn     = findViewById(R.id.btnGuessColumn);
        btnGuessFinal      = findViewById(R.id.btnGuessFinal);
        layoutColumnSelector = findViewById(R.id.layoutColumnSelector);

        btnGuessColumn.setOnClickListener(v -> submitColumnGuess());
        btnGuessFinal.setOnClickListener(v -> submitFinalGuess());

        etGuess.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (isFinalGuessMode) submitFinalGuess();
                else submitColumnGuess();
                return true;
            }
            return false;
        });

        int[] colSelIds = { R.id.btnColA, R.id.btnColB, R.id.btnColC, R.id.btnColD };
        for (int i = 0; i < 4; i++) {
            colSelectors[i] = findViewById(colSelIds[i]);
            final int col = i;
            colSelectors[i].setOnClickListener(v -> selectColumnForGuess(col));
        }

        sectionGameOver = findViewById(R.id.sectionGameOver);
        mainScrollSection = null; // we show/hide the NestedScrollView indirectly
        tvFinalScoreP1  = findViewById(R.id.tvFinalScoreP1);
        tvFinalScoreP2  = findViewById(R.id.tvFinalScoreP2);
        tvWinner        = findViewById(R.id.tvWinner);
        btnGoHome       = findViewById(R.id.btnGoHome);
        btnGoHome.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        isFinalGuessMode = false;
        selectColumnForGuess(0);
    }

    private boolean isFinalGuessMode = false;

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(AsocijacijeViewModel.class);

        viewModel.getQuestion().observe(this, this::onQuestionLoaded);

        viewModel.getCurrentRound().observe(this, r ->
                tvRound.setText("ROUND " + r + " / 2"));

        viewModel.getPlayer1Score().observe(this, s ->
                tvScore.setText(String.valueOf(s)));

        viewModel.getPlayer2Score().observe(this, s ->
                tvScoreOpponent.setText(String.valueOf(s)));

        viewModel.getActivePlayer().observe(this, ap ->
                tvActivePlayer.setText("Player " + ap + "'s turn"));

        viewModel.getBoardState().observe(this, board -> {
            AsocijacijeQuestion q = viewModel.getQuestion().getValue();
            refreshBoard(board, q);
        });

        viewModel.getColumnSolved().observe(this, solved -> {
            AsocijacijeQuestion q = viewModel.getQuestion().getValue();
            if (q == null) return;
            for (int col = 0; col < 4; col++) {
                if (solved[col]) {
                    answerButtons[col].setText(q.getColumnAnswers().get(col));
                    answerButtons[col].setEnabled(false);
                    for (int f = 0; f < 4; f++) {
                        fieldButtons[col][f].setEnabled(false);
                    }
                }
            }
        });

        viewModel.getFinalSolved().observe(this, solved -> {
            if (Boolean.TRUE.equals(solved)) {
                AsocijacijeQuestion q = viewModel.getQuestion().getValue();
                if (q != null) btnFinalAnswer.setText(q.getFinalAnswer());
                btnFinalAnswer.setEnabled(false);
            }
        });

        viewModel.getFeedbackMsg().observe(this, msg -> {
            if (msg != null) tvFeedback.setText(msg);
        });

        viewModel.getGameState().observe(this, state -> {
            switch (state) {
                case LOADING:
                    setInputEnabled(false);
                    break;
                case PLAYER_TURN:
                    setInputEnabled(true);
                    break;
                case ROUND_OVER:
                    cancelTimer();
                    Toast.makeText(this, "Round over! Starting round 2…", Toast.LENGTH_SHORT).show();
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

    private void onQuestionLoaded(AsocijacijeQuestion q) {
        resetBoardUI();
        startRoundTimer();
    }

    private void refreshBoard(boolean[][] board, AsocijacijeQuestion q) {
        if (board == null) return;
        boolean[] solved = viewModel.getColumnSolved().getValue();

        for (int col = 0; col < 4; col++) {
            for (int field = 0; field < 4; field++) {
                MaterialButton btn = fieldButtons[col][field];
                if (board[col][field]) {
                    String clue = "?";

                    if (q != null) {
                        List<String> colList = null;

                        switch (col) {
                            case 0: colList = q.getColumnA(); break;
                            case 1: colList = q.getColumnB(); break;
                            case 2: colList = q.getColumnC(); break;
                            case 3: colList = q.getColumnD(); break;
                        }

                        if (colList != null && field < colList.size()) {
                            clue = colList.get(field);
                        }
                    }
                    btn.setText(clue);
                    btn.setEnabled(false);
                } else {
                    if (solved != null && solved[col]) {
                        btn.setEnabled(false);
                    } else {
                        btn.setText("?");
                        btn.setEnabled(true);
                    }
                }
            }
        }
    }

    private void resetBoardUI() {
        for (int col = 0; col < 4; col++) {
            for (int field = 0; field < 4; field++) {
                fieldButtons[col][field].setText("?");
                fieldButtons[col][field].setEnabled(true);
            }
            answerButtons[col].setText("?");
            answerButtons[col].setEnabled(true);
        }
        btnFinalAnswer.setText("? ? ? ? ?");
        btnFinalAnswer.setEnabled(true);
        tvFeedback.setText("");
        isFinalGuessMode = false;
        selectColumnForGuess(0);
    }

    private void onFieldClicked(int col, int field) {
        boolean opened = viewModel.openField(col, field);
        if (!opened) {
            Toast.makeText(this, "Field already open or column solved!", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectColumnForGuess(int col) {
        selectedColumn = col;
        isFinalGuessMode = false;
        layoutColumnSelector.setVisibility(View.VISIBLE);
        String[] labels = {"A", "B", "C", "D"};
        for (int i = 0; i < 4; i++) {
            boolean sel = i == col;
            colSelectors[i].setTextColor(
                    getResources().getColor(sel ? R.color.accent_ink : R.color.text, null));
            colSelectors[i].setBackgroundTintList(
                    getResources().getColorStateList(sel ? R.color.accent : R.color.bg_card, null));
        }
    }

    private void switchToFinalGuessMode() {
        isFinalGuessMode = true;
        layoutColumnSelector.setVisibility(View.GONE);
        for (int i = 0; i < 4; i++) {
            colSelectors[i].setTextColor(getResources().getColor(R.color.text, null));
            colSelectors[i].setBackgroundTintList(
                    getResources().getColorStateList(R.color.bg_card, null));
        }
        etGuess.requestFocus();
    }

    private void submitColumnGuess() {
        String guess = etGuess.getText() != null ? etGuess.getText().toString().trim() : "";
        if (guess.isEmpty()) return;
        boolean correct = viewModel.guessColumnAnswer(selectedColumn, guess);
        etGuess.setText("");
        if (correct) {
            Toast.makeText(this, "✓ Column correct!", Toast.LENGTH_SHORT).show();
        }
    }

    private void submitFinalGuess() {
        String guess = etGuess.getText() != null ? etGuess.getText().toString().trim() : "";
        if (guess.isEmpty()) return;
        boolean correct = viewModel.guessFinalAnswer(guess);
        etGuess.setText("");
        if (correct) {
            Toast.makeText(this, "Final answer correct!", Toast.LENGTH_SHORT).show();
        }
    }

    private void setInputEnabled(boolean enabled) {
        etGuess.setEnabled(enabled);
        btnGuessColumn.setEnabled(enabled);
        btnGuessFinal.setEnabled(enabled);
        btnFinalAnswer.setEnabled(enabled);
        for (int col = 0; col < 4; col++) {
            for (int field = 0; field < 4; field++) {
                // Only enable fields that are still hidden
                if (enabled) {
                    boolean[][] board = viewModel.getBoardState().getValue();
                    boolean[] solved = viewModel.getColumnSolved().getValue();
                    boolean solvedCol = solved != null && solved[col];
                    boolean open = board != null && board[col][field];
                    fieldButtons[col][field].setEnabled(!open && !solvedCol);
                } else {
                    fieldButtons[col][field].setEnabled(false);
                }
            }
        }
    }

    private void startRoundTimer() {
        cancelTimer();
        roundTimer = new CountDownTimer(ROUND_DURATION_MS, 1000) {
            @Override public void onTick(long ms) {
                long secs = ms / 1000;
                tvTimer.setText(secs / 60 + ":" + String.format("%02d", secs % 60));
            }
            @Override public void onFinish() {
                tvTimer.setText("0:00");
                viewModel.onTimeOut();
            }
        }.start();
    }

    private void cancelTimer() {
        if (roundTimer != null) { roundTimer.cancel(); roundTimer = null; }
    }

    private void showGameOver() {
        View scroll = findViewById(R.id.nestedScrollView);
        if (scroll != null) scroll.setVisibility(View.GONE);
        sectionGameOver.setVisibility(View.VISIBLE);

        int p1 = viewModel.getPlayer1Score().getValue() != null ? viewModel.getPlayer1Score().getValue() : 0;
        int p2 = viewModel.getPlayer2Score().getValue() != null ? viewModel.getPlayer2Score().getValue() : 0;
        tvFinalScoreP1.setText(String.valueOf(p1));
        tvFinalScoreP2.setText(String.valueOf(p2));

        String result;
        if (p1 > p2)       result = "Player 1 wins!";
        else if (p2 > p1)  result = "Player 2 wins!";
        else               result = "It's a draw!";
        tvWinner.setText(result);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimer();
    }
}