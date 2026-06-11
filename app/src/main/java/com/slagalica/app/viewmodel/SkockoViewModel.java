package com.slagalica.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.ValueEventListener;
import com.slagalica.app.model.SkockoQuestion;
import com.slagalica.app.repository.MatchRepository;
import com.slagalica.app.repository.RepositoryCallback;
import com.slagalica.app.repository.SkockoRepository;

import java.util.ArrayList;
import java.util.List;

public class SkockoViewModel extends ViewModel {

    public static final String[] SYMBOLS   = {"♦", "□", "○", "♥", "△", "★"};
    public static final int NUM_SYMBOLS  = 6;
    public static final int CODE_LENGTH  = 4;
    public static final int MAX_ATTEMPTS = 6;
    private static final int PTS_12 = 20;
    private static final int PTS_34  = 15;
    private static final int PTS_56 = 10;
    private static final int BONUS_PTS = 10;

    private final SkockoRepository skockoRepo = new SkockoRepository();
    private final MatchRepository  matchRepo  = new MatchRepository();
    private boolean isMatchGame = false;
    private boolean isPlayer1 = true;
    private String  matchId = null;
    private String  opponentUsername = "Opponent";

    private int prevP1Score = 0;
    private int prevP2Score = 0;
    private boolean timedOut = false;

    private final MutableLiveData<SkockoQuestion> question   = new MutableLiveData<>();
    private final MutableLiveData<GameState> gameState  = new MutableLiveData<>(GameState.LOADING);
    private final MutableLiveData<Integer>  currentRound = new MutableLiveData<>(1);
    private final MutableLiveData<Boolean> isMyTurn  = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> player1Score = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> player2Score = new MutableLiveData<>(0);
    private final MutableLiveData<String>  feedbackMsg = new MutableLiveData<>();
    private final MutableLiveData<String>  errorMessage   = new MutableLiveData<>();
    private final MutableLiveData<Integer>  currentAttempt = new MutableLiveData<>(1);
    private final MutableLiveData<List<GuessEntry>> guessHistory   = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<int[]> finalScores  = new MutableLiveData<>();

    private ValueEventListener questionIdListener;
    private ValueEventListener guessHistoryListener;
    private ValueEventListener bonusListener;
    private ValueEventListener roundDoneListener;
    private ValueEventListener opponentScoreListener;
    private ValueEventListener forfeitListener;

    private boolean p1SolvedR1 = false, p1SolvedR2 = false;
    private boolean p2SolvedR1 = false, p2SolvedR2 = false;
    private int p1AttemptR1 = 0, p1AttemptR2 = 0;
    private int p2AttemptR1 = 0, p2AttemptR2 = 0;
    public LiveData<SkockoQuestion>   getQuestion() { return question; }
    public LiveData<GameState>  getGameState() { return gameState; }
    public LiveData<Integer>  getCurrentRound() { return currentRound; }
    public LiveData<Boolean>  getIsMyTurn()  { return isMyTurn; }
    public LiveData<Integer>  getPlayer1Score()  { return player1Score; }
    public LiveData<Integer> getPlayer2Score()   { return player2Score; }
    public LiveData<String>  getFeedbackMsg() { return feedbackMsg; }
    public LiveData<String>  getErrorMessage() { return errorMessage; }
    public LiveData<Integer> getCurrentAttempt() { return currentAttempt; }
    public LiveData<List<GuessEntry>> getGuessHistory()   { return guessHistory; }
    public LiveData<int[]>  getFinalScores() { return finalScores; }

    public void setInitialScores(int p1Total, int p2Total) {
        prevP1Score = p1Total;
        prevP2Score = p2Total;
        player1Score.setValue(p1Total);
        player2Score.setValue(p2Total);
    }

    public void initMatchMode(String matchId, boolean isPlayer1, String opponentUsername) {
        this.opponentUsername = opponentUsername;
        this.isMatchGame = true;
        this.isPlayer1 = isPlayer1;
        this.matchId = matchId;
        skockoRepo.initMatch(matchId);
        loadQuestion();
    }

    public void loadQuestion() {
        gameState.setValue(GameState.LOADING);
        timedOut = false;
        resetRound();

        if (!isMatchGame) {
            skockoRepo.getRandomQuestion(new RepositoryCallback<SkockoQuestion>() {
                @Override public void onSuccess(SkockoQuestion q) {
                    question.setValue(q);
                    isMyTurn.setValue(true);
                    gameState.setValue(GameState.PLAYER_TURN);
                }
                @Override public void onFailure(Exception e) { errorMessage.setValue(e.getMessage()); }
            });
            return;
        }

        int round = safeGetRound();
        boolean iAmActiveThisRound = (round == 1) ? isPlayer1 : !isPlayer1;

        if (iAmActiveThisRound) {
            skockoRepo.getRandomQuestion(new RepositoryCallback<SkockoQuestion>() {
                @Override public void onSuccess(SkockoQuestion q) {
                    question.setValue(q);
                    skockoRepo.writeQuestionId(q.getId());
                    isMyTurn.setValue(true);
                    gameState.setValue(GameState.PLAYER_TURN);
                    startActivePlayerListeners();
                }
                @Override public void onFailure(Exception e) { errorMessage.setValue(e.getMessage()); }
            });
        } else {
            isMyTurn.setValue(false);
            gameState.setValue(GameState.SPECTATING);
            startSpectatingMode();
        }
    }

    private void startActivePlayerListeners() {
        removeListener(opponentScoreListener);
        opponentScoreListener = skockoRepo.listenForOpponentScore(isPlayer1,
                new RepositoryCallback<Integer>() {
                    @Override public void onSuccess(Integer gameDelta) {
                        if (isPlayer1) player2Score.postValue(prevP2Score + gameDelta);
                        else player1Score.postValue(prevP1Score + gameDelta);
                    }
                    @Override public void onFailure(Exception e) {}
                });
        listenForOpponentForfeit();
    }

    private void startSpectatingMode() {
        int fbRound = safeGetRound() - 1;

        removeListener(questionIdListener);
        removeListener(bonusListener);
        removeListener(roundDoneListener);

        questionIdListener = skockoRepo.listenForQuestionId(new RepositoryCallback<String>() {
            @Override public void onSuccess(String qId) {
                questionIdListener = null;
                skockoRepo.getQuestionById(qId, new RepositoryCallback<SkockoQuestion>() {
                    @Override public void onSuccess(SkockoQuestion q) { question.postValue(q); }
                    @Override public void onFailure(Exception e) {}
                });
            }
            @Override public void onFailure(Exception e) {}
        });

        guessHistoryListener = skockoRepo.listenForGuessHistory(fbRound,
                new RepositoryCallback<List<SkockoRepository.GuessRow>>() {
                    @Override public void onSuccess(List<SkockoRepository.GuessRow> rows) {
                        List<GuessEntry> entries = new ArrayList<>();
                        for (SkockoRepository.GuessRow row : rows) {
                            GuessResult[] res = new GuessResult[CODE_LENGTH];
                            for (int i = 0; i < CODE_LENGTH && i < row.results.size(); i++) {
                                try { res[i] = GuessResult.valueOf(row.results.get(i)); }
                                catch (Exception ex) { res[i] = GuessResult.ABSENT; }
                            }
                            entries.add(new GuessEntry(row.symbols, res));
                        }
                        guessHistory.postValue(entries);
                        currentAttempt.postValue(entries.size() + 1);
                    }
                    @Override public void onFailure(Exception e) {}
                });

        roundDoneListener = skockoRepo.listenForRoundDone(fbRound, !isPlayer1, () -> {
            removeListener(roundDoneListener); roundDoneListener = null;
            removeListener(bonusListener);     bonusListener = null;
            onSpectatingRoundDone();
        });

        bonusListener = skockoRepo.listenForBonusActive(fbRound, () -> {
            removeListener(bonusListener);        bonusListener = null;
            removeListener(guessHistoryListener); guessHistoryListener = null;
            timedOut = false;
            isMyTurn.postValue(true);
            gameState.postValue(GameState.BONUS_TURN);
        });

        listenForOpponentForfeit();
    }

    private void onSpectatingRoundDone() {
        removeListener(guessHistoryListener); guessHistoryListener = null;
        int round = safeGetRound();
        if (round < 2) {
            currentRound.postValue(2);
            gameState.postValue(GameState.ROUND_OVER);
        } else {
            finishGame(true);
        }
    }

    public void onTimerExpired() {
        GameState state = gameState.getValue();
        if (state == GameState.PLAYER_TURN) {
            timedOut = true;
            feedbackMsg.postValue("Time's up! No more attempts.");
            recordSolve(false, 0);
            if (isMatchGame) {
                int fbRound = safeGetRound() - 1;
                skockoRepo.writeBonusActive(fbRound, true);
                gameState.postValue(GameState.WAITING_FOR_BONUS);
                removeListener(roundDoneListener);
                roundDoneListener = skockoRepo.listenForRoundDone(fbRound, !isPlayer1, () -> {
                    removeListener(roundDoneListener); roundDoneListener = null;
                    advanceRound();
                });
            } else {
                gameState.postValue(GameState.BONUS_TURN);
            }
        } else if (state == GameState.BONUS_TURN) {
            timedOut = true;
            submitBonusGuess(null);
        }
    }

    public GuessResult[] submitGuess(List<Integer> guess) {
        if (timedOut) return null;
        if (gameState.getValue() != GameState.PLAYER_TURN) return null;
        if (guess == null || guess.size() != CODE_LENGTH) return null;
        SkockoQuestion q = question.getValue();
        if (q == null) return null;

        GuessResult[] results = evaluate(guess, q.getSolution());
        int attempt = currentAttempt.getValue() != null ? currentAttempt.getValue() : 1;

        List<GuessEntry> history = new ArrayList<>(
                guessHistory.getValue() != null ? guessHistory.getValue() : new ArrayList<>());
        history.add(new GuessEntry(new ArrayList<>(guess), results));
        guessHistory.setValue(history);

        if (isMatchGame) {
            int fbRound = safeGetRound() - 1;
            List<String> resStrings = new ArrayList<>();
            for (GuessResult r : results) resStrings.add(r.name());
            skockoRepo.writeGuessRow(fbRound, attempt - 1, guess, resStrings);
        }

        boolean correct = allCorrect(results);
        if (correct) {
            int pts = pointsForAttempt(attempt);
            addMyPoints(pts);
            recordSolve(true, attempt);
            feedbackMsg.setValue("Correct in attempt " + attempt + "! +" + pts + " pts");
            if (isMatchGame) {
                int fbRound = safeGetRound() - 1;
                skockoRepo.writeRoundDone(fbRound, isPlayer1);
            }
            advanceRound();

        } else if (attempt >= MAX_ATTEMPTS) {
            feedbackMsg.setValue("No more attempts!");
            recordSolve(false, 0);
            if (isMatchGame) {
                int fbRound = safeGetRound() - 1;
                skockoRepo.writeBonusActive(fbRound, true);
                gameState.setValue(GameState.WAITING_FOR_BONUS);
                removeListener(roundDoneListener);
                roundDoneListener = skockoRepo.listenForRoundDone(fbRound, !isPlayer1, () -> {
                    removeListener(roundDoneListener); roundDoneListener = null;
                    advanceRound();
                });
            } else {
                gameState.setValue(GameState.BONUS_TURN);
            }
        } else {
            currentAttempt.setValue(attempt + 1);
        }
        return results;
    }

    public void submitBonusGuess(List<Integer> guess) {
        if (gameState.getValue() != GameState.BONUS_TURN) return;
        SkockoQuestion q = question.getValue();
        int fbRound = safeGetRound() - 1;

        if (q != null && guess != null && guess.size() == CODE_LENGTH) {
            GuessResult[] results = evaluate(guess, q.getSolution());
            if (allCorrect(results)) {
                addMyPoints(BONUS_PTS);
                feedbackMsg.setValue("Bonus correct! +" + BONUS_PTS + " pts");
            } else {
                feedbackMsg.setValue("Bonus missed.");
            }
        } else {
            feedbackMsg.setValue("Bonus time expired.");
        }

        if (isMatchGame) {
            skockoRepo.writeRoundDone(fbRound, isPlayer1);
        }
        advanceRound();
    }

    private void advanceRound() {
        int round = safeGetRound();
        if (round < 2) {
            timedOut = false;
            currentRound.setValue(2);
            gameState.setValue(GameState.ROUND_OVER);
        } else {
            finishGame(true);
        }
    }

    public void startNextRound() {
        timedOut = false;
        resetRound();
        loadQuestion();
    }

    private void finishGame(boolean writeStats) {
        int p1 = safeGet(player1Score);
        int p2 = safeGet(player2Score);

        int p1Delta = p1 - prevP1Score;
        int p2Delta = p2 - prevP2Score;

        if (isMatchGame) {
            matchRepo.writeGameScore(matchId, 3, p1Delta, p2Delta, new RepositoryCallback<Void>() {
                @Override public void onSuccess(Void v) {
                    if (writeStats) {
                        skockoRepo.writeStats(
                                p1SolvedR1, p1AttemptR1, p1SolvedR2, p1AttemptR2,
                                p2SolvedR1, p2AttemptR1, p2SolvedR2, p2AttemptR2,
                                p1Delta, p2Delta,
                                new RepositoryCallback<Void>() {
                                    @Override public void onSuccess(Void v) {
                                        skockoRepo.cleanupMatchData();
                                    }
                                    @Override public void onFailure(Exception e) {
                                        skockoRepo.cleanupMatchData();
                                    }
                                });
                    }
                    finalScores.postValue(new int[]{p1, p2});
                    gameState.postValue(GameState.GAME_OVER);
                }
                @Override public void onFailure(Exception e) {
                    finalScores.postValue(new int[]{p1, p2});
                    gameState.postValue(GameState.GAME_OVER);
                }
            });
        } else {
            finalScores.postValue(new int[]{p1, p2});
            gameState.postValue(GameState.GAME_OVER);
        }
    }

    private void addMyPoints(int pts) {
        if (isPlayer1) {
            int next = safeGet(player1Score) + pts;
            player1Score.setValue(next);
            if (isMatchGame) skockoRepo.writeScore(true, next - prevP1Score);
        } else {
            int next = safeGet(player2Score) + pts;
            player2Score.setValue(next);
            if (isMatchGame) skockoRepo.writeScore(false, next - prevP2Score);
        }
    }

    private void listenForOpponentForfeit() {
        removeListener(forfeitListener);
        String myUid = matchRepo.getUid();
        if (myUid == null) return;
        forfeitListener = matchRepo.listenForForfeit(matchId, myUid, () -> {
            feedbackMsg.postValue(opponentUsername + " left the game.");
            finishGame(true);
        });
    }

    public void writeForfeit() {
        if (!isMatchGame) return;
        String uid = matchRepo.getUid();
        if (uid != null) matchRepo.writeForfeit(matchId, uid);
    }

    private void recordSolve(boolean solved, int attempt) {
        int round = safeGetRound();
        if (isPlayer1) {
            if (round == 1) { p1SolvedR1 = solved; p1AttemptR1 = attempt; }
            else  { p1SolvedR2 = solved; p1AttemptR2 = attempt; }
        } else {
            if (round == 1) { p2SolvedR1 = solved; p2AttemptR1 = attempt; }
            else { p2SolvedR2 = solved; p2AttemptR2 = attempt; }
        }
    }

    private int pointsForAttempt(int attempt) {
        if (attempt <= 2) return PTS_12;
        if (attempt <= 4) return PTS_34;
        return PTS_56;
    }

    private boolean allCorrect(GuessResult[] results) {
        for (GuessResult r : results) if (r != GuessResult.CORRECT) return false;
        return true;
    }

    public static GuessResult[] evaluate(List<Integer> guess, List<Integer> solution) {
        GuessResult[] result    = new GuessResult[CODE_LENGTH];
        boolean[]     solUsed   = new boolean[CODE_LENGTH];
        boolean[]     guessUsed = new boolean[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (guess.get(i).equals(solution.get(i))) {
                result[i]    = GuessResult.CORRECT;
                solUsed[i]   = true;
                guessUsed[i] = true;
            }
        }
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (guessUsed[i]) continue;
            for (int j = 0; j < CODE_LENGTH; j++) {
                if (!solUsed[j] && guess.get(i).equals(solution.get(j))) {
                    solUsed[j] = true;
                    result[i]  = GuessResult.PRESENT;
                    break;
                }
            }
            if (result[i] == null) result[i] = GuessResult.ABSENT;
        }
        return result;
    }

    private void resetRound() {
        currentAttempt.setValue(1);
        guessHistory.setValue(new ArrayList<>());
        feedbackMsg.setValue(null);
    }

    private int safeGetRound() {
        return currentRound.getValue() != null ? currentRound.getValue() : 1;
    }

    private int safeGet(MutableLiveData<Integer> ld) {
        return ld.getValue() != null ? ld.getValue() : 0;
    }

    private void removeListener(ValueEventListener l) {
        skockoRepo.removeListener(l);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        removeListener(questionIdListener);
        removeListener(guessHistoryListener);
        removeListener(bonusListener);
        removeListener(roundDoneListener);
        removeListener(opponentScoreListener);
        removeListener(forfeitListener);
    }

    public enum GameState {
        LOADING, PLAYER_TURN, SPECTATING, BONUS_TURN, WAITING_FOR_BONUS,
        WAITING, ROUND_OVER, GAME_OVER
    }

    public enum GuessResult { CORRECT, PRESENT, ABSENT }

    public static class GuessEntry {
        public final List<Integer> symbols;
        public final GuessResult[] results;
        public GuessEntry(List<Integer> symbols, GuessResult[] results) {
            this.symbols = symbols;
            this.results = results;
        }
    }
}