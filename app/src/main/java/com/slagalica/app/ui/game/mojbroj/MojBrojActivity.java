package com.slagalica.app.ui.game.mojbroj;

import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.slagalica.app.util.GameToast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.slagalica.app.util.ConfirmDialog;

import com.google.android.material.button.MaterialButton;
import com.slagalica.app.R;
import com.slagalica.app.viewmodel.MojBrojViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MojBrojActivity extends AppCompatActivity implements SensorEventListener {

    private static final float SHAKE_THRESHOLD = 12f;
    private static final int STOP_AUTO_MS = 5000;

    private LinearLayout sectionStopTarget;
    private LinearLayout sectionStopNumbers;
    private NestedScrollView sectionPlaying;
    private LinearLayout sectionGameOver;

    private TextView tvRound, tvScore, tvScoreOpponent;
    private TextView tvTargetNumber;


    private TextView tvAnimTarget;
    private TextView[] tvAnimNums;

    private TextView tvGameTitle, tvTimerHeader, tvTargetNumberPlaying;
    private LinearLayout llTokens, panelPlayerYou, panelPlayerOpponent;
    private LinearLayout panelFinalP1, panelFinalP2;
    private TextView tvFinalScoreP1, tvFinalScoreP2, tvWinner;
    private TextView tvFinalNameP1, tvFinalNameP2;
    private String playerUsername = "You";
    private TextView[] numberViews;
    private TextView tvResult;
    private MaterialButton btnPlus, btnMinus, btnMultiply, btnDivide;
    private MaterialButton btnOpenParen, btnCloseParen;
    private MaterialButton btnBackspace, btnClear, btnSubmit;

    private MojBrojViewModel viewModel;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private CountDownTimer roundTimer;
    private CountDownTimer autoStopTimer;

    private final Handler animHandler = new Handler(Looper.getMainLooper());
    private Runnable targetAnimRunnable;
    private Runnable numbersAnimRunnable;
    private final Random animRandom = new Random();

    private static final int[][] ANIM_POOLS = {
        {1,2,3,4,5,6,7,8,9},
        {1,2,3,4,5,6,7,8,9},
        {1,2,3,4,5,6,7,8,9},
        {1,2,3,4,5,6,7,8,9},
        {10,15,20},
        {25,50,75,100}
    };

    private final List<ExprToken> tokens = new ArrayList<>();

    private static class ExprToken {
        enum Type { NUM, OP }
        final Type type;
        final String display;
        final int tileIndex;

        ExprToken(Type type, String display, int tileIndex) {
            this.type = type;
            this.display = display;
            this.tileIndex = tileIndex;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moj_broj);
        playerUsername = getIntent().getStringExtra("username");
        if (playerUsername == null) playerUsername = "You";
        initViews();
        initSensor();
        GameToast.showCountdown(this, this::setupViewModel);
    }

    private void initViews() {
        sectionStopTarget = findViewById(R.id.sectionStopTarget);
        sectionStopNumbers = findViewById(R.id.sectionStopNumbers);
        sectionPlaying = findViewById(R.id.sectionPlaying);
        sectionGameOver = findViewById(R.id.sectionGameOver);
        panelFinalP1 = findViewById(R.id.panelFinalP1);
        panelFinalP2 = findViewById(R.id.panelFinalP2);
        tvFinalNameP1 = findViewById(R.id.tvFinalNameP1);
        tvFinalNameP2 = findViewById(R.id.tvFinalNameP2);
        tvFinalScoreP1 = findViewById(R.id.tvFinalScoreP1);
        tvFinalScoreP2 = findViewById(R.id.tvFinalScoreP2);
        tvWinner = findViewById(R.id.tvWinner);

        tvRound = findViewById(R.id.tvRound);
        tvScore = findViewById(R.id.tvScore);
        tvScoreOpponent = findViewById(R.id.tvScoreOpponent);
        tvTargetNumber = findViewById(R.id.tvTargetNumber);
        tvAnimTarget = findViewById(R.id.tvAnimTarget);
        tvAnimNums = new TextView[]{
            findViewById(R.id.tvAnimNum1), findViewById(R.id.tvAnimNum2),
            findViewById(R.id.tvAnimNum3), findViewById(R.id.tvAnimNum4),
            findViewById(R.id.tvAnimNum5), findViewById(R.id.tvAnimNum6)
        };

        tvGameTitle = findViewById(R.id.tvGameTitle);
        tvGameTitle.setText("My number");
        tvTimerHeader = findViewById(R.id.tvTimer);
        tvTimerHeader.setText("--");
        panelPlayerYou = findViewById(R.id.panelPlayerYou);
        panelPlayerOpponent = findViewById(R.id.panelPlayerOpponent);
        tvTargetNumberPlaying = findViewById(R.id.tvTargetNumberPlaying);
        llTokens = findViewById(R.id.llTokens);

        btnPlus = findViewById(R.id.btnPlus);
        btnMinus = findViewById(R.id.btnMinus);
        btnMultiply = findViewById(R.id.btnMultiply);
        btnDivide = findViewById(R.id.btnDivide);
        btnOpenParen = findViewById(R.id.btnOpenParen);
        btnCloseParen = findViewById(R.id.btnCloseParen);
        btnBackspace = findViewById(R.id.btnBackspace);
        btnClear = findViewById(R.id.btnClear);
        btnSubmit = findViewById(R.id.btnSubmit);

        numberViews = new TextView[]{
            findViewById(R.id.tvNum1), findViewById(R.id.tvNum2),
            findViewById(R.id.tvNum3), findViewById(R.id.tvNum4),
            findViewById(R.id.tvNum5), findViewById(R.id.tvNum6)
        };

        findViewById(R.id.btnClose).setOnClickListener(v -> showExitConfirm());
        sectionStopTarget.setOnClickListener(v -> {
            if (autoStopTimer != null) autoStopTimer.cancel();
            stopTargetAnimation();
            viewModel.stopTarget();
        });
        sectionStopNumbers.setOnClickListener(v -> {
            if (autoStopTimer != null) autoStopTimer.cancel();
            stopNumbersAnimation();
            viewModel.stopNumbers();
        });
        btnSubmit.setOnClickListener(v -> submitCurrentExpression());
        btnClear.setOnClickListener(v -> clearTokens());
        btnBackspace.setOnClickListener(v -> backspace());

        btnPlus.setOnClickListener(v -> addOpToken("+"));
        btnMinus.setOnClickListener(v -> addOpToken("−"));
        btnMultiply.setOnClickListener(v -> addOpToken("×"));
        btnDivide.setOnClickListener(v -> addOpToken("÷"));
        btnOpenParen.setOnClickListener(v -> addOpToken("("));
        btnCloseParen.setOnClickListener(v -> addOpToken(")"));

        for (int i = 0; i < numberViews.length; i++) {
            final int index = i;
            numberViews[i].setOnClickListener(v -> {
                int[] nums = viewModel.getNumbers().getValue();
                if (nums == null) return;
                addNumToken(nums[index], index);
            });
        }

        tvResult = findViewById(R.id.tvResult);
        renderTokens();
        showPhase(-1);
    }

    private void addNumToken(int value, int tileIndex) {
        if (!canAddNumber()) return;
        tokens.add(new ExprToken(ExprToken.Type.NUM, String.valueOf(value), tileIndex));
        numberViews[tileIndex].setEnabled(false);
        numberViews[tileIndex].setAlpha(0.4f);
        numberViews[tileIndex].setBackground(ContextCompat.getDrawable(this, R.drawable.bg_tile_used));
        renderTokens();
    }

    private void addOpToken(String op) {
        if (!canAddOp(op)) return;
        tokens.add(new ExprToken(ExprToken.Type.OP, op, -1));
        renderTokens();
    }

    private boolean canAddNumber() {
        if (tokens.isEmpty()) return true;
        String last = tokens.get(tokens.size() - 1).display;
        return last.equals("+") || last.equals("−") || last.equals("×") || last.equals("÷") || last.equals("(");
    }

    private boolean canAddOp(String op) {
        if (op.equals("(")) {
            if (tokens.isEmpty()) return true;
            String last = tokens.get(tokens.size() - 1).display;
            return last.equals("+") || last.equals("−") || last.equals("×") || last.equals("÷") || last.equals("(");
        }
        if (op.equals(")")) {
            if (tokens.isEmpty()) return false;
            ExprToken lastTok = tokens.get(tokens.size() - 1);
            if (lastTok.type != ExprToken.Type.NUM && !lastTok.display.equals(")")) return false;
            int open = 0;
            for (ExprToken t : tokens) {
                if (t.display.equals("(")) open++;
                else if (t.display.equals(")")) open--;
            }
            return open > 0;
        }
        if (tokens.isEmpty()) return false;
        ExprToken lastTok = tokens.get(tokens.size() - 1);
        return lastTok.type == ExprToken.Type.NUM || lastTok.display.equals(")");
    }

    private void backspace() {
        if (tokens.isEmpty()) return;
        ExprToken last = tokens.remove(tokens.size() - 1);
        if (last.type == ExprToken.Type.NUM) {
            numberViews[last.tileIndex].setEnabled(true);
            numberViews[last.tileIndex].setAlpha(1.0f);
            setTileBackground(last.tileIndex);
        }
        renderTokens();
    }

    private void clearTokens() {
        tokens.clear();
        resetNumberTiles();
        renderTokens();
    }

    private void renderTokens() {
        llTokens.removeAllViews();
        if (tokens.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("tap a number to start...");
            hint.setTextColor(ContextCompat.getColor(this, R.color.text_dim));
            hint.setTextSize(14);
            llTokens.addView(hint);
        } else {
            for (ExprToken t : tokens) {
                TextView chip = new TextView(this);
                chip.setText(t.display);
                chip.setTextSize(16);
                chip.setPadding(dp(10), dp(4), dp(10), dp(4));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.setMarginEnd(dp(4));
                lp.gravity = Gravity.CENTER_VERTICAL;
                chip.setLayoutParams(lp);
                if (t.type == ExprToken.Type.NUM) {
                    chip.setTextColor(ContextCompat.getColor(this, R.color.token));
                    chip.setTypeface(null, Typeface.BOLD);
                    chip.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_token_num));
                } else {
                    chip.setTextColor(ContextCompat.getColor(this, R.color.text_mute));
                    chip.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_token_op));
                }
                llTokens.addView(chip);
            }
        }
        updateResult();
    }

    private void updateResult() {
        if (tvResult == null || viewModel == null) return;
        Integer result = viewModel.tryEvaluate(buildExpression());
        if (result == null) {
            tvResult.setText("");
            return;
        }
        tvResult.setText("= " + result);
        Integer target = viewModel.getTargetNumber().getValue();
        if (target != null && result.equals(target)) {
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.success));
        } else {
            tvResult.setTextColor(ContextCompat.getColor(this, R.color.text_mute));
        }
    }

    private String buildExpression() {
        StringBuilder sb = new StringBuilder();
        for (ExprToken t : tokens) sb.append(t.display);
        return sb.toString();
    }

    private void resetNumberTiles() {
        for (int i = 0; i < numberViews.length; i++) {
            numberViews[i].setEnabled(true);
            numberViews[i].setAlpha(1.0f);
            setTileBackground(i);
        }
    }

    private void setTileBackground(int index) {
        int res = index >= 4 ? R.drawable.bg_tile_large : R.drawable.bg_tile_normal;
        numberViews[index].setBackground(ContextCompat.getDrawable(this, res));
    }

    private void showPhase(int phase) {
        sectionStopTarget.setVisibility(phase == 0 ? View.VISIBLE : View.GONE);
        sectionStopNumbers.setVisibility(phase == 1 ? View.VISIBLE : View.GONE);
        sectionPlaying.setVisibility(phase == 2 ? View.VISIBLE : View.GONE);
        sectionGameOver.setVisibility(phase == 3 ? View.VISIBLE : View.GONE);
    }

    private void setInputEnabled(boolean enabled) {
        btnSubmit.setEnabled(enabled);
        btnClear.setEnabled(enabled);
        btnBackspace.setEnabled(enabled);
        btnPlus.setEnabled(enabled);
        btnMinus.setEnabled(enabled);
        btnMultiply.setEnabled(enabled);
        btnDivide.setEnabled(enabled);
        btnOpenParen.setEnabled(enabled);
        btnCloseParen.setEnabled(enabled);
        for (int i = 0; i < numberViews.length; i++) {
            boolean tileUsed = isTileUsed(i);
            numberViews[i].setEnabled(enabled && !tileUsed);
        }
    }

    private boolean isTileUsed(int index) {
        for (ExprToken t : tokens) {
            if (t.type == ExprToken.Type.NUM && t.tileIndex == index) return true;
        }
        return false;
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    private int safeScore(Integer v) {
        return v == null ? 0 : v;
    }

    private void initSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(MojBrojViewModel.class);

        viewModel.getCurrentRound().observe(this, round ->
            tvRound.setText("Round " + round + " / 2")
        );

        viewModel.getPlayer1Score().observe(this, s -> tvScore.setText(String.valueOf(s)));
        viewModel.getPlayer2Score().observe(this, s -> tvScoreOpponent.setText(String.valueOf(s)));

        viewModel.getTargetNumber().observe(this, target -> {
            if (target != null) {
                String t = String.valueOf(target);
                tvTargetNumber.setText(t);
                tvTargetNumberPlaying.setText(t);
            }
        });

        viewModel.getNumbers().observe(this, nums -> {
            if (nums != null) {
                for (int i = 0; i < numberViews.length; i++) {
                    numberViews[i].setText(String.valueOf(nums[i]));
                    setTileBackground(i);
                }
                numberViews[4].setTextColor(ContextCompat.getColor(this, R.color.token));
                numberViews[5].setTextColor(ContextCompat.getColor(this, R.color.token));
            }
        });

        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) GameToast.show(this, msg);
        });

        viewModel.getGameState().observe(this, state -> {
            switch (state) {
                case SPINNING_TARGET:
                    tvTimerHeader.setText("--");
                    showPhase(0);
                    startTargetAnimation();
                    startAutoStopTimer(() -> {
                        stopTargetAnimation();
                        viewModel.stopTarget();
                    });
                    break;

                case TARGET_REVEALED:
                    if (autoStopTimer != null) autoStopTimer.cancel();
                    stopTargetAnimation();
                    showPhase(1);
                    viewModel.startSpinningNumbers();
                    startAutoStopTimer(() -> {
                        stopNumbersAnimation();
                        viewModel.stopNumbers();
                    });
                    break;

                case SPINNING_NUMBERS:
                    showPhase(1);
                    startNumbersAnimation();
                    break;

                case NUMBERS_REVEALED:
                    if (autoStopTimer != null) autoStopTimer.cancel();
                    showPhase(1);
                    viewModel.beginInputPhase();
                    break;

                case PLAYER1_INPUT:
                    if (roundTimer != null) roundTimer.cancel();
                    showPhase(2);
                    highlightPlayer(1);
                    clearTokens();
                    setInputEnabled(true);
                    startRoundTimer();
                    break;

                case PLAYER2_INPUT:
                    if (roundTimer != null) roundTimer.cancel();
                    showPhase(2);
                    highlightPlayer(2);
                    clearTokens();
                    setInputEnabled(true);
                    startRoundTimer();
                    break;

                case ROUND_OVER:
                    if (roundTimer != null) roundTimer.cancel();
                    setInputEnabled(false);
                    viewModel.startNextRound();
                    break;

                case GAME_OVER:
                    if (roundTimer != null) roundTimer.cancel();
                    setInputEnabled(false);
                    int p1 = safeScore(viewModel.getPlayer1Score().getValue());
                    int p2 = safeScore(viewModel.getPlayer2Score().getValue());
                    tvFinalNameP1.setText(playerUsername);
                    tvFinalNameP2.setText("Opponent");
                    tvFinalScoreP1.setText(String.valueOf(p1));
                    tvFinalScoreP2.setText(String.valueOf(p2));
                    if (p1 > p2) {
                        tvWinner.setText(playerUsername + " wins!");
                        panelFinalP1.setBackgroundResource(R.drawable.bg_player_you);
                        panelFinalP2.setBackgroundResource(R.drawable.bg_player_other);
                    } else if (p2 > p1) {
                        tvWinner.setText("Opponent wins!");
                        panelFinalP1.setBackgroundResource(R.drawable.bg_player_other);
                        panelFinalP2.setBackgroundResource(R.drawable.bg_player_you);
                    } else {
                        tvWinner.setText("Draw!");
                        panelFinalP1.setBackgroundResource(R.drawable.bg_player_other);
                        panelFinalP2.setBackgroundResource(R.drawable.bg_player_other);
                    }
                    showPhase(3);
                    break;
            }
        });
    }

    private void startTargetAnimation() {
        if (targetAnimRunnable != null) animHandler.removeCallbacks(targetAnimRunnable);
        targetAnimRunnable = new Runnable() {
            @Override public void run() {
                tvAnimTarget.setText(String.valueOf(100 + animRandom.nextInt(900)));
                animHandler.postDelayed(this, 80);
            }
        };
        animHandler.post(targetAnimRunnable);
    }

    private void stopTargetAnimation() {
        if (targetAnimRunnable != null) {
            animHandler.removeCallbacks(targetAnimRunnable);
            targetAnimRunnable = null;
        }
    }

    private void startNumbersAnimation() {
        if (numbersAnimRunnable != null) animHandler.removeCallbacks(numbersAnimRunnable);
        numbersAnimRunnable = new Runnable() {
            @Override public void run() {
                for (int i = 0; i < tvAnimNums.length; i++) {
                    int[] pool = ANIM_POOLS[i];
                    tvAnimNums[i].setText(String.valueOf(pool[animRandom.nextInt(pool.length)]));
                }
                animHandler.postDelayed(this, 80);
            }
        };
        animHandler.post(numbersAnimRunnable);
    }

    private void stopNumbersAnimation() {
        if (numbersAnimRunnable != null) {
            animHandler.removeCallbacks(numbersAnimRunnable);
            numbersAnimRunnable = null;
        }
    }

    private void highlightPlayer(int player) {
        panelPlayerYou.setBackgroundResource(
            player == 1 ? R.drawable.bg_player_you : R.drawable.bg_player_other);
        panelPlayerOpponent.setBackgroundResource(
            player == 2 ? R.drawable.bg_player_you : R.drawable.bg_player_other);
    }

    private void startRoundTimer() {
        if (roundTimer != null) roundTimer.cancel();
        tvTimerHeader.setText("60");
        roundTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long ms) {
                tvTimerHeader.setText(String.valueOf(ms / 1000));
            }

            @Override
            public void onFinish() {
                tvTimerHeader.setText("--");
                submitCurrentExpression();
            }
        }.start();
    }

    private void startAutoStopTimer(Runnable action) {
        if (autoStopTimer != null) autoStopTimer.cancel();
        autoStopTimer = new CountDownTimer(STOP_AUTO_MS, 1000) {
            @Override
            public void onTick(long ms) {}

            @Override
            public void onFinish() { action.run(); }
        }.start();
    }

    private void submitCurrentExpression() {
        if (roundTimer != null) roundTimer.cancel();
        setInputEnabled(false);
        boolean exact = viewModel.submitExpression(buildExpression());
        if (exact) GameToast.show(this, "Exact! +10 pts", GameToast.Type.SUCCESS);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (viewModel == null) return;
        float x = event.values[0], y = event.values[1], z = event.values[2];
        float accel = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
        if (accel > SHAKE_THRESHOLD) {
            MojBrojViewModel.GameState state = viewModel.getGameState().getValue();
            if (state == MojBrojViewModel.GameState.SPINNING_TARGET) {
                if (autoStopTimer != null) autoStopTimer.cancel();
                stopTargetAnimation();
                viewModel.stopTarget();
            } else if (state == MojBrojViewModel.GameState.SPINNING_NUMBERS ||
                       state == MojBrojViewModel.GameState.TARGET_REVEALED) {
                if (autoStopTimer != null) autoStopTimer.cancel();
                stopNumbersAnimation();
                viewModel.stopNumbers();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    private void showExitConfirm() {
        ConfirmDialog.show(this, "Quit game?", "Your progress will be lost.",
            "Quit", "Keep playing", this::finish);
    }

    @Override
    public void onBackPressed() {
        showExitConfirm();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roundTimer != null) roundTimer.cancel();
        if (autoStopTimer != null) autoStopTimer.cancel();
        stopTargetAnimation();
        stopNumbersAnimation();
    }
}
