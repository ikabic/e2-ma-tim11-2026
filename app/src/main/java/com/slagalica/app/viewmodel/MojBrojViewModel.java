package com.slagalica.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Random;

public class MojBrojViewModel extends ViewModel {

    private static final int EXACT_POINTS = 10;
    private static final int CLOSER_POINTS = 5;

    private final int[] SMALL_NUMBERS = {10, 15, 20};
    private final int[] LARGE_NUMBERS = {25, 50, 75, 100};

    private final MutableLiveData<Integer> targetNumber = new MutableLiveData<>();
    private final MutableLiveData<int[]> numbers = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentRound = new MutableLiveData<>(1);
    private final MutableLiveData<Integer> player1Score = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> player2Score = new MutableLiveData<>(0);
    private final MutableLiveData<GameState> gameState = new MutableLiveData<>(GameState.SPINNING_TARGET);
    private final MutableLiveData<String> message = new MutableLiveData<>();

    private Integer player1Result = null;
    private Integer player2Result = null;
    private boolean player1HasSubmitted = false;
    private boolean player2HasSubmitted = false;
    private int roundStarter = 1;
    private final Random random = new Random();

    public enum GameState {
        SPINNING_TARGET,
        TARGET_REVEALED,
        SPINNING_NUMBERS,
        NUMBERS_REVEALED,
        PLAYER1_INPUT,
        PLAYER2_INPUT,
        ROUND_OVER,
        GAME_OVER
    }

    public LiveData<Integer> getTargetNumber() { return targetNumber; }
    public LiveData<int[]> getNumbers() { return numbers; }
    public LiveData<Integer> getCurrentRound() { return currentRound; }
    public LiveData<Integer> getPlayer1Score() { return player1Score; }
    public LiveData<Integer> getPlayer2Score() { return player2Score; }
    public LiveData<GameState> getGameState() { return gameState; }
    public LiveData<String> getMessage() { return message; }
    public int getRoundStarter() { return roundStarter; }

    public void stopTarget() {
        if (gameState.getValue() != GameState.SPINNING_TARGET) return;
        int target = 100 + random.nextInt(900);
        targetNumber.setValue(target);
        gameState.setValue(GameState.TARGET_REVEALED);
    }

    public void stopNumbers() {
        if (gameState.getValue() != GameState.SPINNING_NUMBERS &&
            gameState.getValue() != GameState.TARGET_REVEALED) return;
        int[] nums = new int[6];
        for (int i = 0; i < 4; i++) nums[i] = 1 + random.nextInt(9);
        nums[4] = SMALL_NUMBERS[random.nextInt(SMALL_NUMBERS.length)];
        nums[5] = LARGE_NUMBERS[random.nextInt(LARGE_NUMBERS.length)];
        numbers.setValue(nums);
        gameState.setValue(GameState.NUMBERS_REVEALED);
    }

    public void startSpinningNumbers() {
        gameState.setValue(GameState.SPINNING_NUMBERS);
    }

    public void beginInputPhase() {
        player1HasSubmitted = false;
        player2HasSubmitted = false;
        player1Result = null;
        player2Result = null;
        gameState.setValue(roundStarter == 1 ? GameState.PLAYER1_INPUT : GameState.PLAYER2_INPUT);
    }

    public boolean submitExpression(String expression) {
        GameState state = gameState.getValue();
        if (state != GameState.PLAYER1_INPUT && state != GameState.PLAYER2_INPUT) return false;

        Integer result = null;
        boolean exact = false;
        if (!expression.isEmpty()) {
            try {
                result = evaluate(expression);
                exact = targetNumber.getValue() != null && result.equals(targetNumber.getValue());
            } catch (Exception e) {
                result = null;
            }
        }

        if (state == GameState.PLAYER1_INPUT) {
            player1Result = result;
            player1HasSubmitted = true;
            if (!player2HasSubmitted) gameState.setValue(GameState.PLAYER2_INPUT);
        } else {
            player2Result = result;
            player2HasSubmitted = true;
            if (!player1HasSubmitted) gameState.setValue(GameState.PLAYER1_INPUT);
        }

        if (player1HasSubmitted && player2HasSubmitted) {
            calculateScores();
            advanceRound();
        }

        return exact;
    }

    private void calculateScores() {
        int target = targetNumber.getValue();
        boolean p1Exact = player1Result != null && player1Result == target;
        boolean p2Exact = player2Result != null && player2Result == target;

        if (p1Exact && p2Exact) {
            player1Score.setValue(player1Score.getValue() + EXACT_POINTS);
            player2Score.setValue(player2Score.getValue() + EXACT_POINTS);
        } else if (p1Exact) {
            player1Score.setValue(player1Score.getValue() + EXACT_POINTS);
        } else if (p2Exact) {
            player2Score.setValue(player2Score.getValue() + EXACT_POINTS);
        } else {
            if (player1Result == null && player2Result == null) return;
            if (player1Result == null) { player2Score.setValue(player2Score.getValue() + CLOSER_POINTS); return; }
            if (player2Result == null) { player1Score.setValue(player1Score.getValue() + CLOSER_POINTS); return; }

            int diff1 = Math.abs(player1Result - target);
            int diff2 = Math.abs(player2Result - target);

            if (player1Result.equals(player2Result)) {
                if (roundStarter == 1) player1Score.setValue(player1Score.getValue() + CLOSER_POINTS);
                else player2Score.setValue(player2Score.getValue() + CLOSER_POINTS);
            } else if (diff1 < diff2) {
                player1Score.setValue(player1Score.getValue() + CLOSER_POINTS);
            } else if (diff2 < diff1) {
                player2Score.setValue(player2Score.getValue() + CLOSER_POINTS);
            } else {
                if (roundStarter == 1) player1Score.setValue(player1Score.getValue() + CLOSER_POINTS);
                else player2Score.setValue(player2Score.getValue() + CLOSER_POINTS);
            }
        }
    }

    private void advanceRound() {
        if (currentRound.getValue() < 2) {
            currentRound.setValue(2);
            roundStarter = 2;
            targetNumber.setValue(null);
            numbers.setValue(null);
            gameState.setValue(GameState.ROUND_OVER);
        } else {
            gameState.setValue(GameState.GAME_OVER);
        }
    }

    public void startNextRound() {
        gameState.setValue(GameState.SPINNING_TARGET);
    }

    public Integer tryEvaluate(String expression) {
        if (expression.isEmpty()) return null;
        try {
            return evaluate(expression);
        } catch (Exception e) {
            return null;
        }
    }

    private int evaluate(String expression) {
        expression = expression.replace("×", "*").replace("÷", "/").replace("−", "-").trim();
        return (int) new ExpressionEvaluator(expression).evaluate();
    }

    private static class ExpressionEvaluator {
        private final String expr;
        private int pos = 0;

        ExpressionEvaluator(String expr) { this.expr = expr; }

        double evaluate() { return parseExpression(); }

        private double parseExpression() {
            double result = parseTerm();
            while (pos < expr.length() && (expr.charAt(pos) == '+' || expr.charAt(pos) == '-')) {
                char op = expr.charAt(pos++);
                result = op == '+' ? result + parseTerm() : result - parseTerm();
            }
            return result;
        }

        private double parseTerm() {
            double result = parseFactor();
            while (pos < expr.length() && (expr.charAt(pos) == '*' || expr.charAt(pos) == '/')) {
                char op = expr.charAt(pos++);
                double next = parseFactor();
                if (op == '/' && next == 0) throw new ArithmeticException("Division by zero");
                result = op == '*' ? result * next : result / next;
            }
            return result;
        }

        private double parseFactor() {
            while (pos < expr.length() && expr.charAt(pos) == ' ') pos++;
            if (pos < expr.length() && expr.charAt(pos) == '(') {
                pos++;
                double result = parseExpression();
                if (pos < expr.length() && expr.charAt(pos) == ')') pos++;
                return result;
            }
            int start = pos;
            if (pos < expr.length() && expr.charAt(pos) == '-') pos++;
            while (pos < expr.length() && Character.isDigit(expr.charAt(pos))) pos++;
            return Double.parseDouble(expr.substring(start, pos));
        }
    }
}
