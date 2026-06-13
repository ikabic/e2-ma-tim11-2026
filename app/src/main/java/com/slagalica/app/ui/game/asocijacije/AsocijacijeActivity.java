package com.slagalica.app.ui.game.asocijacije;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

    private TextView tvRound, tvTimer, tvScore, tvScoreOpponent, tvActivePlayer, tvFeedback;
    private MaterialButton[][] fieldButtons  = new MaterialButton[4][4];
    private MaterialButton[] answerButtons = new MaterialButton[4];
    private MaterialButton btnFinalAnswer;
    private TextInputEditText etGuess;
    private MaterialButton btnGuessColumn, btnGuessFinal, btnPassTurn;
    private LinearLayout layoutColumnSelector;
    private MaterialButton[] colSelectors = new MaterialButton[4];
    private View sectionGameOver;
    private TextView  tvFinalScoreP1, tvFinalScoreP2, tvWinner;
    private MaterialButton btnGoHome;
    private int  selectedColumn  = 0;
    private boolean  isFinalGuessMode = false;
    private CountDownTimer roundTimer;
    private AsocijacijeViewModel viewModel;
    private boolean isMatchGame = false;
    private boolean isPlayer1Local = true;
    private String  opponentUsername = "Opponent";
    private boolean matchFinishedNormally = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asocijacije);

        isMatchGame = getIntent().getBooleanExtra("isMatchGame", false);
        isPlayer1Local  = getIntent().getBooleanExtra("isPlayer1", true);
        String matchId  = getIntent().getStringExtra("matchId");
        opponentUsername = getIntent().getStringExtra("opponentUsername");
        if (opponentUsername == null) opponentUsername = "Opponent";

        int prevTotalP1 = getIntent().getIntExtra("prevTotalP1", 0);
        int prevTotalP2 = getIntent().getIntExtra("prevTotalP2", 0);

        initViews();
        setupViewModel();

        new AsocijacijeRepository().seedTestData();

        if (isMatchGame && matchId != null) {
            viewModel.setInitialScores(prevTotalP1, prevTotalP2);
            if (getIntent().getBooleanExtra("soloContinue", false)) {
                viewModel.initSoloMode(matchId, isPlayer1Local);
            } else {
                viewModel.initMatchMode(matchId, isPlayer1Local, opponentUsername);
            }
        } else {
            viewModel.loadQuestion();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTimer();
        // Only forfeit on abnormal exit (back press, app kill).
        // Normal game completion sets matchFinishedNormally = true first.
        if (isMatchGame && !matchFinishedNormally) {
            viewModel.writeForfeit();
        }
    }

    private void initViews() {
        tvRound  = findViewById(R.id.tvRound);
        tvTimer = findViewById(R.id.tvTimer);
        tvScore = findViewById(R.id.tvScore);
        tvScoreOpponent = findViewById(R.id.tvScoreOpponent);
        tvActivePlayer = findViewById(R.id.tvActivePlayer);
        tvFeedback = findViewById(R.id.tvFeedback);

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

        btnFinalAnswer = findViewById(R.id.btnFinalAnswer);
        btnFinalAnswer.setOnClickListener(v -> switchToFinalGuessMode());

        etGuess = findViewById(R.id.etGuess);
        btnGuessColumn = findViewById(R.id.btnGuessColumn);
        btnGuessFinal = findViewById(R.id.btnGuessFinal);
        btnPassTurn = findViewById(R.id.btnPassTurn);

        btnGuessColumn.setOnClickListener(v -> submitColumnGuess());
        btnGuessFinal.setOnClickListener(v -> submitFinalGuess());
        btnPassTurn.setOnClickListener(v -> { viewModel.passTurn(); etGuess.setText(""); });

        etGuess.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (isFinalGuessMode) submitFinalGuess(); else submitColumnGuess();
                return true;
            }
            return false;
        });

        layoutColumnSelector = findViewById(R.id.layoutColumnSelector);
        int[] colSelIds = { R.id.btnColA, R.id.btnColB, R.id.btnColC, R.id.btnColD };
        for (int i = 0; i < 4; i++) {
            colSelectors[i] = findViewById(colSelIds[i]);
            final int col = i;
            colSelectors[i].setOnClickListener(v -> selectColumnForGuess(col));
        }

        sectionGameOver = findViewById(R.id.sectionGameOver);
        tvFinalScoreP1  = findViewById(R.id.tvFinalScoreP1);
        tvFinalScoreP2  = findViewById(R.id.tvFinalScoreP2);
        tvWinner  = findViewById(R.id.tvWinner);
        btnGoHome = findViewById(R.id.btnGoHome);
        btnGoHome.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        selectColumnForGuess(0);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(AsocijacijeViewModel.class);

        viewModel.getQuestion().observe(this, this::onQuestionLoaded);

        viewModel.getCurrentRound().observe(this, r -> {
            if (r == null) return;
            tvRound.setText("ROUND " + r + " / 2");
        });

        viewModel.getTimerStartMs().observe(this, remainingMs -> {
            if (remainingMs == null) return;
            cancelTimer();
            startRoundTimer(remainingMs);
        });

        viewModel.getPlayer1Score().observe(this, s -> {
            if (s == null) return;
            if (isPlayer1Local) tvScore.setText(String.valueOf(s));
            else tvScoreOpponent.setText(String.valueOf(s));
        });
        viewModel.getPlayer2Score().observe(this, s -> {
            if (s == null) return;
            if (isPlayer1Local) tvScoreOpponent.setText(String.valueOf(s));
            else tvScore.setText(String.valueOf(s));
        });

        viewModel.getIsMyTurn().observe(this, myTurn -> {
            if (myTurn == null) return;
            AsocijacijeViewModel.GameState state = viewModel.getGameState().getValue();
            if (state == AsocijacijeViewModel.GameState.PLAYER_TURN
                    || state == AsocijacijeViewModel.GameState.OPPONENT_TURN) {
                tvActivePlayer.setText(Boolean.TRUE.equals(myTurn) ? "Your turn" : opponentUsername + "'s turn");
            }
        });

        viewModel.getBoardState().observe(this, board ->
                refreshBoard(board, viewModel.getQuestion().getValue()));

        viewModel.getColumnSolved().observe(this, solved -> {
            AsocijacijeQuestion q = viewModel.getQuestion().getValue();
            if (q == null || solved == null) return;
            for (int col = 0; col < 4; col++) {
                if (!solved[col]) continue;
                answerButtons[col].setText(q.getColumnAnswers().get(col));
                answerButtons[col].setEnabled(false);
                List<String> colList = getColumnList(q, col);
                for (int f = 0; f < 4; f++) {
                    if (colList != null && f < colList.size())
                        fieldButtons[col][f].setText(colList.get(f));
                    fieldButtons[col][f].setEnabled(false);
                    fieldButtons[col][f].setAlpha(1f);
                }
            }
        });

        viewModel.getFinalSolved().observe(this, solved -> {
            if (!Boolean.TRUE.equals(solved)) return;
            AsocijacijeQuestion q = viewModel.getQuestion().getValue();
            if (q == null) return;
            btnFinalAnswer.setText(q.getFinalAnswer());
            btnFinalAnswer.setEnabled(false);
            btnFinalAnswer.setAlpha(1f);
            for (int col = 0; col < 4; col++) {
                answerButtons[col].setText(q.getColumnAnswers().get(col));
                answerButtons[col].setEnabled(false);
                List<String> colList = getColumnList(q, col);
                for (int f = 0; f < 4; f++) {
                    if (colList != null && f < colList.size())
                        fieldButtons[col][f].setText(colList.get(f));
                    fieldButtons[col][f].setEnabled(false);
                    fieldButtons[col][f].setAlpha(1f);
                }
            }
            setInputEnabled(false);
        });

        viewModel.getFeedbackMsg().observe(this, msg -> {
            if (msg != null) tvFeedback.setText(msg);
        });

        viewModel.getGameState().observe(this, state -> {
            switch (state) {
                case LOADING:
                    setInputEnabled(false);
                    tvActivePlayer.setText("Loading...");
                    break;
                case PLAYER_TURN:
                    setInputEnabled(true);
                    tvActivePlayer.setText("Your turn");
                    break;
                case OPPONENT_TURN:
                    setInputEnabled(false);
                    tvActivePlayer.setText(opponentUsername + "'s turn");
                    break;
                case WAITING_FOR_ROUND_END:
                    cancelTimer();
                    tvTimer.setText("--:--");
                    setInputEnabled(false);
                    tvActivePlayer.setText("Round ending...");
                    break;
                case ROUND_OVER:
                    cancelTimer();
                    setInputEnabled(false);
                    Toast.makeText(this, "Round 1 done! Starting Round 2...", Toast.LENGTH_SHORT).show();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        resetBoardUI();
                        viewModel.startNextRound();
                    }, 500);
                    break;
                case GAME_OVER:
                    cancelTimer();
                    setInputEnabled(false);
                    break;
            }
        });

        viewModel.getFinalScores().observe(this, scores -> {
            if (scores == null) return;
            if (isMatchGame) {
                // Mark as normal finish BEFORE calling finish() so onDestroy
                // does not trigger a spurious forfeit write.
                matchFinishedNormally = true;
                Intent result = new Intent();
                result.putExtra("p1Score", scores[0]);
                result.putExtra("p2Score", scores[1]);
                setResult(RESULT_OK, result);
                finish();
            } else {
                showGameOver();
            }
        });

        viewModel.getErrorMessage().observe(this, err -> {
            if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
        });
    }

    private void onQuestionLoaded(AsocijacijeQuestion q) {
        if (q == null) return;
        resetBoardUI();
        Boolean myTurn = viewModel.getIsMyTurn().getValue();
        AsocijacijeViewModel.GameState state = viewModel.getGameState().getValue();
        setInputEnabled(Boolean.TRUE.equals(myTurn) && state == AsocijacijeViewModel.GameState.PLAYER_TURN);
    }

    private void refreshBoard(boolean[][] board, AsocijacijeQuestion q) {
        if (board == null || q == null) return;
        boolean[] solved = viewModel.getColumnSolved().getValue();
        Boolean myTurn   = viewModel.getIsMyTurn().getValue();
        AsocijacijeViewModel.GameState state = viewModel.getGameState().getValue();
        boolean canInteract = Boolean.TRUE.equals(myTurn) && state == AsocijacijeViewModel.GameState.PLAYER_TURN;

        for (int col = 0; col < 4; col++) {
            boolean solvedCol = solved != null && solved[col];
            List<String> colList = getColumnList(q, col);
            for (int field = 0; field < 4; field++) {
                MaterialButton btn = fieldButtons[col][field];
                if (board[col][field] || solvedCol) {
                    String clue = (colList != null && field < colList.size()) ? colList.get(field) : "?";
                    btn.setText(clue);
                    btn.setEnabled(false);
                    btn.setAlpha(1f);
                } else {
                    btn.setText("?");
                    btn.setEnabled(canInteract && !solvedCol);
                    btn.setAlpha(1f);
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

    private List<String> getColumnList(AsocijacijeQuestion q, int col) {
        if (q == null) return null;
        switch (col) {
            case 0: return q.getColumnA();
            case 1: return q.getColumnB();
            case 2: return q.getColumnC();
            case 3: return q.getColumnD();
            default: return null;
        }
    }

    private void onFieldClicked(int col, int field) {
        if (!viewModel.openField(col, field))
            Toast.makeText(this, "Field already open or column solved!", Toast.LENGTH_SHORT).show();
    }

    private void selectColumnForGuess(int col) {
        selectedColumn   = col;
        isFinalGuessMode = false;
        layoutColumnSelector.setVisibility(View.VISIBLE);
        for (int i = 0; i < 4; i++) {
            boolean sel = i == col;
            colSelectors[i].setTextColor(getResources().getColor(sel ? R.color.accent_ink : R.color.text, null));
            colSelectors[i].setBackgroundTintList(getResources().getColorStateList(sel ? R.color.accent : R.color.bg_card, null));
        }
    }

    private void switchToFinalGuessMode() {
        isFinalGuessMode = true;
        layoutColumnSelector.setVisibility(View.GONE);
        for (int i = 0; i < 4; i++) {
            colSelectors[i].setTextColor(getResources().getColor(R.color.text, null));
            colSelectors[i].setBackgroundTintList(getResources().getColorStateList(R.color.bg_card, null));
        }
        etGuess.requestFocus();
    }

    private void submitColumnGuess() {
        String guess = etGuess.getText() != null ? etGuess.getText().toString().trim() : "";
        if (guess.isEmpty()) return;
        if (viewModel.guessColumnAnswer(selectedColumn, guess))
            Toast.makeText(this, "✓ Column correct!", Toast.LENGTH_SHORT).show();
        etGuess.setText("");
    }

    private void submitFinalGuess() {
        String guess = etGuess.getText() != null ? etGuess.getText().toString().trim() : "";
        if (guess.isEmpty()) return;
        if (viewModel.guessFinalAnswer(guess))
            Toast.makeText(this, "Final answer correct!", Toast.LENGTH_SHORT).show();
        etGuess.setText("");
    }

    private void setInputEnabled(boolean enabled) {
        etGuess.setEnabled(enabled);
        btnGuessColumn.setEnabled(enabled);
        btnGuessFinal.setEnabled(enabled);
        btnPassTurn.setEnabled(enabled);
        btnFinalAnswer.setEnabled(enabled && !Boolean.TRUE.equals(viewModel.getFinalSolved().getValue()));

        boolean[][] board = viewModel.getBoardState().getValue();
        boolean[]   solved = viewModel.getColumnSolved().getValue();
        boolean alreadyOpened = viewModel.hasOpenedField();

        for (int col = 0; col < 4; col++) {
            boolean solvedCol = solved != null && solved[col];
            for (int field = 0; field < 4; field++) {
                boolean isOpen = board != null && board[col][field];
                fieldButtons[col][field].setEnabled(
                        enabled && !isOpen && !solvedCol && !alreadyOpened);
                fieldButtons[col][field].setAlpha(1f);
            }
            answerButtons[col].setAlpha(1f);
        }
        btnFinalAnswer.setAlpha(1f);
    }

    private void startRoundTimer(long durationMs) {
        cancelTimer();
        roundTimer = new CountDownTimer(durationMs, 1000) {
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
        int p1 = viewModel.getPlayer1Score().getValue() != null ? viewModel.getPlayer1Score().getValue() : 0;
        int p2 = viewModel.getPlayer2Score().getValue() != null ? viewModel.getPlayer2Score().getValue() : 0;

        View scroll = findViewById(R.id.nestedScrollView);
        if (scroll != null) scroll.setVisibility(View.GONE);
        sectionGameOver.setVisibility(View.VISIBLE);

        tvFinalScoreP1.setText(String.valueOf(p1));
        tvFinalScoreP2.setText(String.valueOf(p2));
        tvWinner.setText(p1 > p2 ? "Player 1 wins!" : p2 > p1 ? "Player 2 wins!" : "It's a draw!");
    }
}