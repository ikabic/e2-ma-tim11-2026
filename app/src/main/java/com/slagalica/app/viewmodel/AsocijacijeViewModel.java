package com.slagalica.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.ValueEventListener;
import com.slagalica.app.model.AsocijacijeQuestion;
import com.slagalica.app.repository.AsocijacijeRepository;
import com.slagalica.app.repository.MatchRepository;
import com.slagalica.app.repository.RepositoryCallback;

import java.util.Map;

public class AsocijacijeViewModel extends ViewModel {

    private static final int NUM_COLS = 4;
    private static final int NUM_FIELDS = 4;
    private static final int COL_BASE = 2;
    private static final int FINAL_BASE = 7;
    private static final int FINAL_CLOSED_COL_BONUS = 6;
    private static final long ROUND_DURATION_MS = 2 * 60 * 1000L;
    private static final long ROUND_END_DELAY_MS = 3000L;
    private final AsocijacijeRepository asocRepo = new AsocijacijeRepository();
    private final MatchRepository matchRepo = new MatchRepository();
    private boolean isMatchGame = false;
    private boolean isPlayer1 = true;
    private String  matchId = null;
    private String  opponentUsername = "Opponent";
    private final MutableLiveData<AsocijacijeQuestion> question     = new MutableLiveData<>();
    private final MutableLiveData<GameState>  gameState   = new MutableLiveData<>(GameState.LOADING);
    private final MutableLiveData<Integer>    currentRound= new MutableLiveData<>(1);
    private final MutableLiveData<Boolean> isMyTurn = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> player1Score= new MutableLiveData<>(0);
    private final MutableLiveData<Integer> player2Score= new MutableLiveData<>(0);
    private final MutableLiveData<String> feedbackMsg = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage= new MutableLiveData<>();
    private final MutableLiveData<boolean[][]>boardState  = new MutableLiveData<>(new boolean[NUM_COLS][NUM_FIELDS]);
    private final MutableLiveData<boolean[]>  columnSolved= new MutableLiveData<>(new boolean[NUM_COLS]);
    private final MutableLiveData<Boolean> finalSolved = new MutableLiveData<>(false);
    private final MutableLiveData<int[]> finalScores = new MutableLiveData<>();
    private final MutableLiveData<Long> timerStartMs= new MutableLiveData<>();
    private ValueEventListener activePlayerListener;
    private ValueEventListener boardListener;
    private ValueEventListener solvedListener;
    private ValueEventListener lastActionListener;
    private ValueEventListener opponentScoreListener;
    private ValueEventListener opponentDoneListener;
    private ValueEventListener questionIdListener;
    private ValueEventListener forfeitListener;
    private ValueEventListener roundStartTimeListener;
    private int roundStartTimeListenerRound = 0;
    private int prevP1Score = 0;
    private int prevP2Score = 0;
    private int myColsSolved = 0;
    private boolean myFinalSolved = false;
    private int myFinalsSolved = 0;
    private int oppColsSolved = 0;
    private boolean oppFinalSolved  = false;
    private int oppFinalsSolved = 0;
    private boolean hasOpenedFieldInThisTurn = false;
    private boolean roundEndScheduled = false;
    public LiveData<AsocijacijeQuestion> getQuestion()     { return question; }
    public LiveData<GameState> getGameState()    { return gameState; }
    public LiveData<Integer>  getCurrentRound() { return currentRound; }
    public LiveData<Boolean>  getIsMyTurn()     { return isMyTurn; }
    public LiveData<Integer> getPlayer1Score() { return player1Score; }
    public LiveData<Integer> getPlayer2Score() { return player2Score; }
    public LiveData<String> getFeedbackMsg()  { return feedbackMsg; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<boolean[][]>getBoardState()   { return boardState; }
    public LiveData<boolean[]> getColumnSolved() { return columnSolved; }
    public LiveData<Boolean> getFinalSolved()  { return finalSolved; }
    public LiveData<int[]> getFinalScores()  { return finalScores; }
    public LiveData<Long>  getTimerStartMs() { return timerStartMs; }
    public boolean hasOpenedField()  { return hasOpenedFieldInThisTurn; }

    public void setInitialScores(int p1Subtotal, int p2Subtotal) {
        this.prevP1Score = p1Subtotal;
        this.prevP2Score = p2Subtotal;
    }

    public void initMatchMode(String matchId, boolean isPlayer1, String opponentUsername) {
        this.opponentUsername = opponentUsername;
        this.isMatchGame = true;
        this.isPlayer1 = isPlayer1;
        this.matchId = matchId;

        player1Score.setValue(prevP1Score);
        player2Score.setValue(prevP2Score);

        asocRepo.initMatch(matchId);

        if (isPlayer1) {
            asocRepo.writeInitialScores(prevP1Score, prevP2Score);
        } else {
            asocRepo.listenForInitialScores(new RepositoryCallback<int[]>() {
                @Override public void onSuccess(int[] scores) {
                    prevP1Score = scores[0];
                    prevP2Score = scores[1];
                    player1Score.postValue(prevP1Score);
                    player2Score.postValue(prevP2Score);
                }
                @Override public void onFailure(Exception e) {}
            });
        }

        loadQuestion();
    }

    public void loadQuestion() {
        gameState.setValue(GameState.LOADING);
        resetBoard();

        if (!isMatchGame) {
            asocRepo.getRandomQuestion(new RepositoryCallback<AsocijacijeQuestion>() {
                @Override public void onSuccess(AsocijacijeQuestion q) {
                    question.setValue(q);
                    isMyTurn.setValue(true);
                    gameState.setValue(GameState.PLAYER_TURN);
                    timerStartMs.setValue(ROUND_DURATION_MS);
                }
                @Override public void onFailure(Exception e) { errorMessage.setValue(e.getMessage()); }
            });
            return;
        }

        int round = safeGet(currentRound);
        boolean iPickQuestion = (round == 1) == isPlayer1;

        if (iPickQuestion) {
            asocRepo.getRandomQuestion(new RepositoryCallback<AsocijacijeQuestion>() {
                @Override public void onSuccess(AsocijacijeQuestion q) {
                    question.setValue(q);
                    asocRepo.writeQuestionId(q.getId());
                    asocRepo.writeActivePlayer(round == 1);
                    startMatchListeners();
                }
                @Override public void onFailure(Exception e) { errorMessage.setValue(e.getMessage()); }
            });
        } else {
            questionIdListener = asocRepo.listenForQuestionId(new RepositoryCallback<String>() {
                @Override public void onSuccess(String qId) {
                    questionIdListener = null;
                    asocRepo.getQuestionById(qId, new RepositoryCallback<AsocijacijeQuestion>() {
                        @Override public void onSuccess(AsocijacijeQuestion q) {
                            question.setValue(q);
                            startMatchListeners();
                        }
                        @Override public void onFailure(Exception e) { errorMessage.setValue(e.getMessage()); }
                    });
                }
                @Override public void onFailure(Exception e) { errorMessage.setValue(e.getMessage()); }
            });
        }
    }

    private void startMatchListeners() {
        // Remove all old listeners first
        asocRepo.removeListener(activePlayerListener);
        asocRepo.removeListener(boardListener);
        asocRepo.removeListener(solvedListener);
        asocRepo.removeListener(lastActionListener);
        asocRepo.removeListener(opponentScoreListener);
        asocRepo.removeListener(forfeitListener);
        activePlayerListener  = null;
        boardListener = null;
        solvedListener = null;
        lastActionListener = null;
        opponentScoreListener = null;
        forfeitListener = null;

        activePlayerListener = asocRepo.listenForActivePlayer(new RepositoryCallback<Boolean>() {
            @Override public void onSuccess(Boolean isP1Turn) {
                if (roundEndScheduled) return;
                boolean myTurn = isPlayer1 == isP1Turn;
                isMyTurn.postValue(myTurn);
                if (myTurn) hasOpenedFieldInThisTurn = false;
                GameState cur = gameState.getValue();
                if (cur != GameState.GAME_OVER && cur != GameState.WAITING_FOR_ROUND_END && cur != GameState.ROUND_OVER) {
                    gameState.postValue(myTurn ? GameState.PLAYER_TURN : GameState.OPPONENT_TURN);
                }
            }
            @Override public void onFailure(Exception e) {}
        });

        boardListener = asocRepo.listenForBoard(new RepositoryCallback<Map<String, Boolean>>() {
            @Override public void onSuccess(Map<String, Boolean> remoteBoard) {
                boolean[][] board = new boolean[NUM_COLS][NUM_FIELDS];
                for (Map.Entry<String, Boolean> e : remoteBoard.entrySet()) {
                    String[] parts = e.getKey().split("_");
                    if (parts.length == 2) {
                        try {
                            int col   = Integer.parseInt(parts[0]);
                            int field = Integer.parseInt(parts[1]);
                            if (col < NUM_COLS && field < NUM_FIELDS)
                                board[col][field] = Boolean.TRUE.equals(e.getValue());
                        } catch (NumberFormatException ignored) {}
                    }
                }
                boardState.postValue(board);
            }
            @Override public void onFailure(Exception e) {}
        });

        solvedListener = asocRepo.listenForSolvedState((cols, fs, finalSolvedPlayer) -> {
            columnSolved.postValue(cols);
            finalSolved.postValue(fs);

            if (finalSolvedPlayer != null && !finalSolvedPlayer.isEmpty()) {
                String myRole = isPlayer1 ? "p1" : "p2";
                if (finalSolvedPlayer.equals(myRole)) myFinalsSolved++;
                else oppFinalsSolved++;
            }

            if (fs) scheduleRoundEnd();
        });

        lastActionListener = asocRepo.listenForLastAction(new RepositoryCallback<Map<String, Object>>() {
            @Override public void onSuccess(Map<String, Object> action) {
                if (Boolean.TRUE.equals(isMyTurn.getValue())) return;
                String type = (String)  action.get("type");
                Boolean correct = (Boolean) action.get("correct");
                String answer = (String)  action.get("answer");
                if (type == null) return;
                switch (type) {
                    case "guessColumn":
                        feedbackMsg.postValue(Boolean.TRUE.equals(correct) ? "Opponent guessed column: " + answer + " ✓"
                                : "Opponent guessed wrong: " + answer + " ✗");
                        break;
                    case "guessFinal":
                        feedbackMsg.postValue(Boolean.TRUE.equals(correct) ? "Opponent got the final answer: " + answer + " "
                                : "Opponent's final guess wrong: " + answer + " ✗");
                        break;
                    case "openField":
                        feedbackMsg.postValue("Opponent opened a field...");
                        break;
                }
            }
            @Override public void onFailure(Exception e) {}
        });

        opponentScoreListener = asocRepo.listenForOpponentScore(isPlayer1,
                new RepositoryCallback<Integer>() {
                    @Override public void onSuccess(Integer gameScore) {
                        if (isPlayer1) player2Score.postValue(prevP2Score + gameScore);
                        else           player1Score.postValue(prevP1Score + gameScore);
                    }
                    @Override public void onFailure(Exception e) {}
                });

        String myUid = matchRepo.getUid();
        if (myUid != null) {
            forfeitListener = matchRepo.listenForForfeit(matchId, myUid, () -> {
                feedbackMsg.postValue(opponentUsername + " left the game.");
                scheduleRoundEnd();
            });
        }

        int round = safeGet(currentRound);
        startSynchronizedTimer(round);
    }

    private void startSynchronizedTimer(int round) {
        if (roundStartTimeListener != null) {
            asocRepo.removeRoundStartTimeListener(roundStartTimeListenerRound, roundStartTimeListener);
            roundStartTimeListener = null;
        }
        roundStartTimeListenerRound = round;
        asocRepo.writeRoundStartTime(round);

        roundStartTimeListener = asocRepo.listenForRoundStartTime(round,
                new RepositoryCallback<Long>() {
                    @Override public void onSuccess(Long startTime) {
                        ValueEventListener l = roundStartTimeListener;
                        roundStartTimeListener = null;
                        if (l != null) asocRepo.removeRoundStartTimeListener(round, l);

                        long elapsed = System.currentTimeMillis() - startTime;
                        long remaining = ROUND_DURATION_MS - elapsed;
                        if (remaining <= 0) {
                            onTimeOut();
                        } else {
                            timerStartMs.postValue(remaining);
                        }
                    }
                    @Override public void onFailure(Exception e) {
                        timerStartMs.postValue(ROUND_DURATION_MS);
                    }
                });
    }

    private void scheduleRoundEnd() {
        if (roundEndScheduled) return;
        roundEndScheduled = true;

        asocRepo.removeListener(activePlayerListener);
        activePlayerListener = null;

        revealAllFieldsLocally();
        gameState.postValue(GameState.WAITING_FOR_ROUND_END);

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (safeGet(currentRound) < 2) {
                gameState.postValue(GameState.ROUND_OVER);
            } else {
                finishGame();
            }
        }, ROUND_END_DELAY_MS);
    }

    public void onTimeOut() {
        if (!isMatchGame) { advanceRound(); return; }
        scheduleRoundEnd();
    }

    public void startNextRound() {
        roundEndScheduled = false;
        asocRepo.setRound(2);
        currentRound.setValue(2);
        resetBoard();
        loadQuestion();
    }

    public boolean openField(int col, int field) {
        if (!canAct()) return false;
        if (hasOpenedFieldInThisTurn) {
            feedbackMsg.setValue("You've already opened a field this turn. Guess or pass.");
            return false;
        }
        boolean[][] board = boardState.getValue();
        if (board == null || board[col][field]) return false;
        boolean[] solved = columnSolved.getValue();
        if (solved != null && solved[col]) return false;
        if (Boolean.TRUE.equals(finalSolved.getValue())) return false;

        board[col][field] = true;
        boardState.setValue(board);

        if (isMatchGame) {
            asocRepo.writeFieldOpened(col, field);
            asocRepo.writeLastAction("openField", col, false, "");
            hasOpenedFieldInThisTurn = true;
        }
        return true;
    }

    public boolean guessColumnAnswer(int col, String guess) {
        if (!canAct()) return false;
        AsocijacijeQuestion q = question.getValue();
        if (q == null) return false;
        boolean[] solved = columnSolved.getValue();
        if (solved != null && solved[col]) return false;

        boolean correct = guess.trim().equalsIgnoreCase(q.getColumnAnswers().get(col));

        if (isMatchGame) {
            asocRepo.writeLastAction("guessColumn", col, correct, guess);
            if (correct) {
                asocRepo.writeColumnSolved(col);
                myColsSolved++;
                int pts = calcColumnPoints(col);
                addMyPoints(pts);
                feedbackMsg.setValue("Column correct! +" + pts + " pts");
            } else {
                feedbackMsg.setValue("Wrong — opponent's turn");
                hasOpenedFieldInThisTurn = false;
                asocRepo.writeActivePlayer(!isPlayer1);
            }
        } else {
            if (correct) {
                solved[col] = true;
                columnSolved.setValue(solved);
                int pts = calcColumnPoints(col);
                addMyPoints(pts);
                feedbackMsg.setValue("Column correct! +" + pts + " pts");
                checkIfRoundOver();
            } else {
                feedbackMsg.setValue("Wrong answer");
                switchPlayer();
            }
        }
        return correct;
    }

    public boolean guessFinalAnswer(String guess) {
        if (!canAct()) return false;
        AsocijacijeQuestion q = question.getValue();
        if (q == null) return false;
        if (Boolean.TRUE.equals(finalSolved.getValue())) return false;

        boolean correct = guess.trim().equalsIgnoreCase(q.getFinalAnswer());

        if (isMatchGame) {
            asocRepo.writeLastAction("guessFinal", -1, correct, guess);
            if (correct) {
                int pts = calcFinalPoints();
                myFinalSolved = true;
                addMyPoints(pts);
                feedbackMsg.setValue("Final answer correct! +" + pts + " pts");
                asocRepo.writeFinalSolved(true, isPlayer1);
                scheduleRoundEnd();
            } else {
                feedbackMsg.setValue("Wrong final answer — opponent's turn");
                hasOpenedFieldInThisTurn = false;
                asocRepo.writeActivePlayer(!isPlayer1);
            }
        } else {
            if (correct) {
                finalSolved.setValue(true);
                myFinalSolved = true;
                int pts = calcFinalPoints();
                addMyPoints(pts);
                feedbackMsg.setValue("Final answer correct! +" + pts + " pts");
                scheduleRoundEnd();
            } else {
                feedbackMsg.setValue("Wrong final answer");
                switchPlayer();
            }
        }
        return correct;
    }

    public void passTurn() {
        if (!isMatchGame || !Boolean.TRUE.equals(isMyTurn.getValue())) return;
        if (gameState.getValue() != GameState.PLAYER_TURN) return;
        feedbackMsg.setValue("You passed the turn.");
        hasOpenedFieldInThisTurn = false;
        asocRepo.writeActivePlayer(!isPlayer1);
    }

    private void finishGame() {
        int p1 = safeGet(player1Score);
        int p2 = safeGet(player2Score);

        matchRepo.writeGameScore(matchId, 2, p1, p2, new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void v) {}
            @Override public void onFailure(Exception e) {}
        });

        asocRepo.writeStats(isPlayer1 ? myFinalsSolved : oppFinalsSolved, isPlayer1 ? oppFinalsSolved : myFinalsSolved,
                new RepositoryCallback<Void>() {
                    @Override public void onSuccess(Void v) { asocRepo.cleanupMatchData(); }
                    @Override public void onFailure(Exception e) { asocRepo.cleanupMatchData(); }
                });

        finalScores.postValue(new int[]{p1, p2});
        gameState.postValue(GameState.GAME_OVER);
    }

    private void addMyPoints(int pts) {
        if (isPlayer1) {
            int next = safeGet(player1Score) + pts;
            player1Score.setValue(next);
            if (isMatchGame) {
                asocRepo.writeScore(true, next - prevP1Score);
            }
        } else {
            int next = safeGet(player2Score) + pts;
            player2Score.setValue(next);
            if (isMatchGame) {
                asocRepo.writeScore(false, next - prevP2Score);
            }
        }
    }

    private int calcColumnPoints(int col) {
        boolean[][] board = boardState.getValue();
        int hidden = 0;
        if (board != null)
            for (int f = 0; f < NUM_FIELDS; f++)
                if (!board[col][f]) hidden++;
        return COL_BASE + hidden;
    }

    private int calcFinalPoints() {
        boolean[][] board  = boardState.getValue();
        boolean[] solved = columnSolved.getValue();
        int pts = FINAL_BASE;
        for (int col = 0; col < NUM_COLS; col++) {
            if (solved != null && solved[col]) continue;
            boolean anyOpen = false;
            if (board != null)
                for (int f = 0; f < NUM_FIELDS; f++)
                    if (board[col][f]) { anyOpen = true; break; }
            if (!anyOpen) pts += FINAL_CLOSED_COL_BONUS;
            pts += calcColumnPoints(col);
        }
        return pts;
    }

    private void revealAllFieldsLocally() {
        boolean[][] board = new boolean[NUM_COLS][NUM_FIELDS];
        for (int c = 0; c < NUM_COLS; c++)
            for (int f = 0; f < NUM_FIELDS; f++)
                board[c][f] = true;
        boardState.postValue(board);

        boolean[] cols = new boolean[NUM_COLS];
        for (int c = 0; c < NUM_COLS; c++) cols[c] = true;
        columnSolved.postValue(cols);
    }

    private void switchPlayer() {
        if (!isMatchGame) isMyTurn.setValue(!Boolean.TRUE.equals(isMyTurn.getValue()));
    }

    private void checkIfRoundOver() {
        boolean[] solved = columnSolved.getValue();
        boolean allSolved = true;
        if (solved != null) for (boolean b : solved) if (!b) { allSolved = false; break; }
        if (allSolved || Boolean.TRUE.equals(finalSolved.getValue())) advanceRound();
    }

    private void advanceRound() {
        if (safeGet(currentRound) < 2) {
            currentRound.setValue(2);
            gameState.setValue(GameState.ROUND_OVER);
        } else {
            gameState.setValue(GameState.GAME_OVER);
        }
    }

    private boolean canAct() {
        if (!isMatchGame) return gameState.getValue() == GameState.PLAYER_TURN;
        return Boolean.TRUE.equals(isMyTurn.getValue()) && gameState.getValue() == GameState.PLAYER_TURN;
    }

    private void resetBoard() {
        boardState.setValue(new boolean[NUM_COLS][NUM_FIELDS]);
        columnSolved.setValue(new boolean[NUM_COLS]);
        finalSolved.setValue(false);
        feedbackMsg.setValue(null);
        myColsSolved  = 0; myFinalSolved  = false;
        oppColsSolved = 0; oppFinalSolved = false;
        hasOpenedFieldInThisTurn = false;
        roundEndScheduled = false;
    }

    private int safeGet(MutableLiveData<Integer> ld) {
        return ld.getValue() != null ? ld.getValue() : 0;
    }

    public void writeForfeit() {
        if (!isMatchGame) return;
        String uid = matchRepo.getUid();
        if (uid != null) matchRepo.writeForfeit(matchId, uid);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        asocRepo.removeListener(activePlayerListener);
        asocRepo.removeListener(boardListener);
        asocRepo.removeListener(solvedListener);
        asocRepo.removeListener(lastActionListener);
        asocRepo.removeListener(opponentScoreListener);
        asocRepo.removeListener(opponentDoneListener);
        asocRepo.removeListener(questionIdListener);
        asocRepo.removeListener(forfeitListener);
        if (roundStartTimeListener != null) {
            asocRepo.removeRoundStartTimeListener(roundStartTimeListenerRound, roundStartTimeListener);
        }
    }

    public enum GameState {
        LOADING, PLAYER_TURN, OPPONENT_TURN, WAITING_FOR_ROUND_END, ROUND_OVER, GAME_OVER
    }
}