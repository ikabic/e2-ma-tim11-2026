package com.slagalica.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.slagalica.app.model.SkockoQuestion;
import com.slagalica.app.repository.RepositoryCallback;
import com.slagalica.app.repository.SkockoRepository;

import java.util.ArrayList;
import java.util.List;
public class SkockoViewModel extends ViewModel {

    public static final String[] SYMBOLS = { "♦", "□", "○", "♥", "△", "★" };
    public static final int NUM_SYMBOLS  = 6;
    public static final int CODE_LENGTH  = 4;
    public static final int MAX_ATTEMPTS = 6;
    private static final int PTS_12 = 20;
    private static final int PTS_34 = 15;
    private static final int PTS_56 = 10;
    private static final int BONUS_PTS = 10;
    private final SkockoRepository repository;
    private final MutableLiveData<SkockoQuestion>   question      = new MutableLiveData<>();
    private final MutableLiveData<GameState>        gameState     = new MutableLiveData<>(GameState.LOADING);
    private final MutableLiveData<Integer>          currentRound  = new MutableLiveData<>(1);
    private final MutableLiveData<Integer>          activePlayer  = new MutableLiveData<>(1);
    private final MutableLiveData<Integer>          player1Score  = new MutableLiveData<>(0);
    private final MutableLiveData<Integer>          player2Score  = new MutableLiveData<>(0);
    private final MutableLiveData<String>           feedbackMsg   = new MutableLiveData<>();
    private final MutableLiveData<String>           errorMessage  = new MutableLiveData<>();
    private final MutableLiveData<Integer>          currentAttempt= new MutableLiveData<>(1);
    private final MutableLiveData<List<GuessEntry>> guessHistory  = new MutableLiveData<>(new ArrayList<>());

    public SkockoViewModel() {
        repository = new SkockoRepository();
    }
    public LiveData<SkockoQuestion>   getQuestion()       { return question; }
    public LiveData<GameState>        getGameState()      { return gameState; }
    public LiveData<Integer>          getCurrentRound()   { return currentRound; }
    public LiveData<Integer>          getActivePlayer()   { return activePlayer; }
    public LiveData<Integer>          getPlayer1Score()   { return player1Score; }
    public LiveData<Integer>          getPlayer2Score()   { return player2Score; }
    public LiveData<String>           getFeedbackMsg()    { return feedbackMsg; }
    public LiveData<String>           getErrorMessage()   { return errorMessage; }
    public LiveData<Integer>          getCurrentAttempt() { return currentAttempt; }
    public LiveData<List<GuessEntry>> getGuessHistory()   { return guessHistory; }

    public void loadQuestion() {
        gameState.setValue(GameState.LOADING);
        resetRound();
        repository.getRandomQuestion(new RepositoryCallback<SkockoQuestion>() {
            @Override public void onSuccess(SkockoQuestion result) {
                question.setValue(result);
                activePlayer.setValue(currentRound.getValue() == 1 ? 1 : 2);
                gameState.setValue(GameState.PLAYER_TURN);
            }
            @Override public void onFailure(Exception e) {
                errorMessage.setValue(e.getMessage());
            }
        });
    }

    public GuessResult[] submitGuess(List<Integer> guess) {
        if (gameState.getValue() != GameState.PLAYER_TURN) return null;
        if (guess == null || guess.size() != CODE_LENGTH) return null;

        SkockoQuestion q = question.getValue();
        if (q == null) return null;

        GuessResult[] results = evaluate(guess, q.getSolution());

        List<GuessEntry> history = new ArrayList<>(
                guessHistory.getValue() != null ? guessHistory.getValue() : new ArrayList<>());
        history.add(new GuessEntry(new ArrayList<>(guess), results));
        guessHistory.setValue(history);

        int attemptNum = currentAttempt.getValue() != null ? currentAttempt.getValue() : 1;

        boolean correct = allCorrect(results);
        if (correct) {
            int pts = pointsForAttempt(attemptNum);
            addPointsToActivePlayer(pts);
            feedbackMsg.setValue("Correct in attempt " + attemptNum + "! +" + pts + " pts");
            advanceRound();
        } else if (attemptNum >= MAX_ATTEMPTS) {
            feedbackMsg.setValue("No more attempts — opponent gets a bonus try!");
            gameState.setValue(GameState.BONUS_TURN);
        } else {
            currentAttempt.setValue(attemptNum + 1);
        }

        return results;
    }
    public void submitBonusGuess(List<Integer> guess) {
        if (gameState.getValue() != GameState.BONUS_TURN) return;
        SkockoQuestion q = question.getValue();
        if (q == null) { advanceRound(); return; }

        if (guess != null && guess.size() == CODE_LENGTH) {
            GuessResult[] results = evaluate(guess, q.getSolution());
            if (allCorrect(results)) {
                addPointsToOpponent(BONUS_PTS);
                feedbackMsg.setValue("Opponent guessed correctly! +" + BONUS_PTS + " pts");
            } else {
                feedbackMsg.setValue("Opponent missed — no points.");
            }
        } else {
            feedbackMsg.setValue("Bonus time expired.");
        }
        advanceRound();
    }

    public void startNextRound() {
        loadQuestion();
    }

    public static GuessResult[] evaluate(List<Integer> guess, List<Integer> solution) {
        GuessResult[] result = new GuessResult[CODE_LENGTH];
        boolean[] solutionUsed = new boolean[CODE_LENGTH];
        boolean[] guessUsed    = new boolean[CODE_LENGTH];

        for (int i = 0; i < CODE_LENGTH; i++) {
            if (guess.get(i).equals(solution.get(i))) {
                result[i]       = GuessResult.CORRECT;
                solutionUsed[i] = true;
                guessUsed[i]    = true;
            }
        }

        for (int i = 0; i < CODE_LENGTH; i++) {
            if (guessUsed[i]) continue;
            boolean found = false;
            for (int j = 0; j < CODE_LENGTH; j++) {
                if (!solutionUsed[j] && guess.get(i).equals(solution.get(j))) {
                    solutionUsed[j] = true;
                    found = true;
                    break;
                }
            }
            result[i] = found ? GuessResult.PRESENT : GuessResult.ABSENT;
        }
        return result;
    }

    private boolean allCorrect(GuessResult[] results) {
        for (GuessResult r : results) if (r != GuessResult.CORRECT) return false;
        return true;
    }

    private int pointsForAttempt(int attempt) {
        if (attempt <= 2) return PTS_12;
        if (attempt <= 4) return PTS_34;
        return PTS_56;
    }

    private void advanceRound() {
        if (safeGet(currentRound) < 2) {
            currentRound.setValue(2);
            gameState.setValue(GameState.ROUND_OVER);
        } else {
            gameState.setValue(GameState.GAME_OVER);
        }
    }

    private void resetRound() {
        currentAttempt.setValue(1);
        guessHistory.setValue(new ArrayList<>());
        feedbackMsg.setValue(null);
    }

    private void addPointsToActivePlayer(int pts) {
        if (safeGet(activePlayer) == 1) {
            player1Score.setValue(safeGet(player1Score) + pts);
        } else {
            player2Score.setValue(safeGet(player2Score) + pts);
        }
    }

    private void addPointsToOpponent(int pts) {
        if (safeGet(activePlayer) == 1) {
            player2Score.setValue(safeGet(player2Score) + pts);
        } else {
            player1Score.setValue(safeGet(player1Score) + pts);
        }
    }

    private int safeGet(MutableLiveData<Integer> ld) {
        return ld.getValue() != null ? ld.getValue() : 0;
    }

    public enum GameState { LOADING, PLAYER_TURN, BONUS_TURN, ROUND_OVER, GAME_OVER }

    public enum GuessResult {
        CORRECT,
        PRESENT,
        ABSENT
    }

    public static class GuessEntry {
        public final List<Integer>   symbols;
        public final GuessResult[]   results;

        public GuessEntry(List<Integer> symbols, GuessResult[] results) {
            this.symbols = symbols;
            this.results = results;
        }
    }
}