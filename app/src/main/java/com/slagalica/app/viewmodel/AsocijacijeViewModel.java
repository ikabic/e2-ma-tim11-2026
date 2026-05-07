package com.slagalica.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.slagalica.app.model.AsocijacijeQuestion;
import com.slagalica.app.repository.AsocijacijeRepository;
import com.slagalica.app.repository.RepositoryCallback;

public class AsocijacijeViewModel extends ViewModel {
    private static final int NUM_COLS    = 4;
    private static final int NUM_FIELDS  = 4;
    private static final int COL_BASE    = 2;
    private static final int FINAL_BASE  = 7;
    private static final int FINAL_CLOSED_COL_BONUS = 6;

    private final AsocijacijeRepository repository;

    private final MutableLiveData<AsocijacijeQuestion> question    = new MutableLiveData<>();
    private final MutableLiveData<GameState>           gameState   = new MutableLiveData<>(GameState.LOADING);
    private final MutableLiveData<Integer>             currentRound= new MutableLiveData<>(1);
    private final MutableLiveData<Integer>             activePlayer= new MutableLiveData<>(1);
    private final MutableLiveData<Integer>             player1Score= new MutableLiveData<>(0);
    private final MutableLiveData<Integer>             player2Score= new MutableLiveData<>(0);
    private final MutableLiveData<String>              feedbackMsg = new MutableLiveData<>();
    private final MutableLiveData<String>              errorMessage= new MutableLiveData<>();
    private final MutableLiveData<boolean[][]> boardState = new MutableLiveData<>(new boolean[NUM_COLS][NUM_FIELDS]);
    private final MutableLiveData<boolean[]> columnSolved = new MutableLiveData<>(new boolean[NUM_COLS]);
    private final MutableLiveData<Boolean> finalSolved = new MutableLiveData<>(false);

    private int round1Starter = 1;

    public AsocijacijeViewModel() {
        repository = new AsocijacijeRepository();
    }

    public LiveData<AsocijacijeQuestion> getQuestion()      { return question; }
    public LiveData<GameState>           getGameState()     { return gameState; }
    public LiveData<Integer>             getCurrentRound()  { return currentRound; }
    public LiveData<Integer>             getActivePlayer()  { return activePlayer; }
    public LiveData<Integer>             getPlayer1Score()  { return player1Score; }
    public LiveData<Integer>             getPlayer2Score()  { return player2Score; }
    public LiveData<String>              getFeedbackMsg()   { return feedbackMsg; }
    public LiveData<String>              getErrorMessage()  { return errorMessage; }
    public LiveData<boolean[][]>         getBoardState()    { return boardState; }
    public LiveData<boolean[]>           getColumnSolved()  { return columnSolved; }
    public LiveData<Boolean>             getFinalSolved()   { return finalSolved; }

    public void loadQuestion() {
        gameState.setValue(GameState.LOADING);
        resetBoard();
        repository.getRandomQuestion(new RepositoryCallback<AsocijacijeQuestion>() {
            @Override public void onSuccess(AsocijacijeQuestion result) {
                question.setValue(result);
                activePlayer.setValue(currentRound.getValue() == 1 ? 1 : 2);
                gameState.setValue(GameState.PLAYER_TURN);
            }
            @Override public void onFailure(Exception e) {
                errorMessage.setValue(e.getMessage());
            }
        });
    }

    public boolean openField(int col, int field) {
        if (gameState.getValue() != GameState.PLAYER_TURN) return false;
        boolean[][] board = boardState.getValue();
        if (board == null || board[col][field]) return false;
        boolean[] solved = columnSolved.getValue();
        if (solved != null && solved[col]) return false;
        if (Boolean.TRUE.equals(finalSolved.getValue())) return false;

        board[col][field] = true;
        boardState.setValue(board);

        return true;
    }

    public boolean guessColumnAnswer(int col, String guess) {
        if (gameState.getValue() != GameState.PLAYER_TURN) return false;
        AsocijacijeQuestion q = question.getValue();
        if (q == null) return false;
        boolean[] solved = columnSolved.getValue();
        if (solved == null || solved[col]) return false;

        if (guess.trim().equalsIgnoreCase(q.getColumnAnswers().get(col))) {
            solved[col] = true;
            columnSolved.setValue(solved);

            int pts = calcColumnPoints(col);
            addPointsToActivePlayer(pts);
            feedbackMsg.setValue("Column " + (char)('A' + col) + " correct! +" + pts + " pts");

            checkIfRoundOver();
            return true;
        } else {
            feedbackMsg.setValue("Wrong — opponent's turn");
            switchPlayer();
            return false;
        }
    }

    public boolean guessFinalAnswer(String guess) {
        if (gameState.getValue() != GameState.PLAYER_TURN) return false;
        AsocijacijeQuestion q = question.getValue();
        if (q == null) return false;
        if (Boolean.TRUE.equals(finalSolved.getValue())) return false;

        if (guess.trim().equalsIgnoreCase(q.getFinalAnswer())) {
            finalSolved.setValue(true);
            int pts = calcFinalPoints();
            addPointsToActivePlayer(pts);
            feedbackMsg.setValue("Final answer correct! +" + pts + " pts");
            advanceRound();
            return true;
        } else {
            feedbackMsg.setValue("Wrong final — opponent's turn");
            switchPlayer();
            return false;
        }
    }

    public void onTimeOut() {
        feedbackMsg.setValue("Time's up!");
        advanceRound();
    }

    public void startNextRound() {
        loadQuestion();
    }

    private int calcColumnPoints(int col) {
        boolean[][] board = boardState.getValue();
        int hidden = 0;
        if (board != null) {
            for (int f = 0; f < NUM_FIELDS; f++) {
                if (!board[col][f]) hidden++;
            }
        }
        return COL_BASE + hidden;
    }

    private int calcFinalPoints() {
        boolean[][] board = boardState.getValue();
        boolean[] solved  = columnSolved.getValue();
        int pts = FINAL_BASE;

        for (int col = 0; col < NUM_COLS; col++) {
            boolean anyOpen = false;
            if (board != null) {
                for (int f = 0; f < NUM_FIELDS; f++) {
                    if (board[col][f]) { anyOpen = true; break; }
                }
            }
            boolean isSolved = solved != null && solved[col];

            if (!anyOpen && !isSolved) {
                pts += FINAL_CLOSED_COL_BONUS;
            }
        }
        return pts;
    }

    private void switchPlayer() {
        int current = activePlayer.getValue() != null ? activePlayer.getValue() : 1;
        activePlayer.setValue(current == 1 ? 2 : 1);
    }

    private void addPointsToActivePlayer(int pts) {
        int ap = activePlayer.getValue() != null ? activePlayer.getValue() : 1;
        if (ap == 1) {
            player1Score.setValue(safeGet(player1Score) + pts);
        } else {
            player2Score.setValue(safeGet(player2Score) + pts);
        }
    }

    private void checkIfRoundOver() {
        boolean[] solved = columnSolved.getValue();
        boolean allColsSolved = true;
        if (solved != null) {
            for (boolean b : solved) if (!b) { allColsSolved = false; break; }
        }
        if (allColsSolved || Boolean.TRUE.equals(finalSolved.getValue())) {
            advanceRound();
        }
    }

    private void advanceRound() {
        if (safeGet(currentRound) < 2) {
            currentRound.setValue(2);
            gameState.setValue(GameState.ROUND_OVER);
        } else {
            gameState.setValue(GameState.GAME_OVER);
        }
    }

    private void resetBoard() {
        boardState.setValue(new boolean[NUM_COLS][NUM_FIELDS]);
        columnSolved.setValue(new boolean[NUM_COLS]);
        finalSolved.setValue(false);
        feedbackMsg.setValue(null);
    }

    private int safeGet(MutableLiveData<Integer> ld) {
        return ld.getValue() != null ? ld.getValue() : 0;
    }

    public enum GameState {
        LOADING,
        PLAYER_TURN,
        ROUND_OVER,
        GAME_OVER
    }
}