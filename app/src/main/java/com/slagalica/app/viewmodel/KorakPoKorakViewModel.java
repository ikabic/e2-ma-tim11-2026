package com.slagalica.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.slagalica.app.model.KorakPoKorakQuestion;
import com.slagalica.app.repository.KorakPoKorakRepository;
import com.slagalica.app.repository.RepositoryCallback;

public class KorakPoKorakViewModel extends ViewModel {

    private static final int MAX_STEPS = 7;
    private static final int MAX_POINTS = 20;
    private static final int POINTS_PER_STEP = 2;
    private static final int BONUS_POINTS = 5;

    private final KorakPoKorakRepository repository;

    private final MutableLiveData<KorakPoKorakQuestion> question     = new MutableLiveData<>();
    private final MutableLiveData<KorakPoKorakQuestion> stealQuestion = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentStep = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> currentRound = new MutableLiveData<>(1);
    private final MutableLiveData<Integer> player1Score = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> player2Score = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> soloStealBonus = new MutableLiveData<>(0);
    private final MutableLiveData<GameState> gameState = new MutableLiveData<>(GameState.LOADING);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private boolean isMatchGame = false;
    private boolean isPlayer1 = true;
    private String matchId = null;

    public enum GameState {
        LOADING,
        PLAYER_TURN,
        OPPONENT_BONUS,
        STEAL_ATTEMPT,
        SOLO_STEAL,
        SOLO_STEAL_DONE,
        ROUND_OVER,
        GAME_OVER
    }

    public KorakPoKorakViewModel() {
        repository = new KorakPoKorakRepository();
    }

    public LiveData<KorakPoKorakQuestion> getQuestion() { return question; }
    public LiveData<KorakPoKorakQuestion> getStealQuestion() { return stealQuestion; }
    public LiveData<Integer> getCurrentStep() { return currentStep; }
    public LiveData<Integer> getCurrentRound() { return currentRound; }
    public LiveData<Integer> getPlayer1Score() { return player1Score; }
    public LiveData<Integer> getPlayer2Score() { return player2Score; }
    public LiveData<Integer> getSoloStealBonus() { return soloStealBonus; }
    public LiveData<GameState> getGameState() { return gameState; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public void initMatchMode(String matchId, boolean isPlayer1) {
        this.isMatchGame = true;
        this.isPlayer1 = isPlayer1;
        this.matchId = matchId;
    }

    public void loadQuestion() {
        gameState.setValue(GameState.LOADING);
        repository.getRandomQuestion(new RepositoryCallback<KorakPoKorakQuestion>() {
            @Override
            public void onSuccess(KorakPoKorakQuestion result) {
                question.setValue(result);
                currentStep.setValue(0);
                gameState.setValue(GameState.PLAYER_TURN);
            }

            @Override
            public void onFailure(Exception e) {
                errorMessage.setValue(e.getMessage());
            }
        });
    }

    public boolean submitAnswer(String userAnswer) {
        KorakPoKorakQuestion q = question.getValue();
        if (q == null || userAnswer.isEmpty()) return false;

        if (isCorrect(userAnswer, q)) {
            int step = currentStep.getValue();
            int points = MAX_POINTS - (step * POINTS_PER_STEP);

            if (isMatchGame)
                repository.writeStats(matchId, isPlayer1, step + 1, null);

            addPointsToCurrentPlayer(points);
            advanceRound();
            return true;
        }
        return false;
    }

    private boolean isCorrect(String userAnswer, KorakPoKorakQuestion q) {
        if (q.getAnswers() == null) return false;
        String trimmed = userAnswer.trim();
        for (String accepted : q.getAnswers()) {
            if (accepted.equalsIgnoreCase(trimmed)) return true;
        }
        return false;
    }

    public void onStepTimeout() {
        int step = currentStep.getValue();
        if (step < MAX_STEPS - 1) {
            currentStep.setValue(step + 1);
        } else {
            gameState.setValue(GameState.OPPONENT_BONUS);
        }
    }

    public boolean submitBonusAnswer(String userAnswer) {
        if (gameState.getValue() != GameState.OPPONENT_BONUS) return false;
        KorakPoKorakQuestion q = question.getValue();
        if (q == null) return false;

        gameState.setValue(GameState.LOADING);

        if (!userAnswer.isEmpty() && isCorrect(userAnswer, q)) {
            addPointsToOpponent(BONUS_POINTS);
            advanceRound();
            return true;
        }
        advanceRound();
        return false;
    }

    private void addPointsToCurrentPlayer(int points) {
        if (currentRound.getValue() == 1) {
            player1Score.setValue(player1Score.getValue() + points);
        } else {
            player2Score.setValue(player2Score.getValue() + points);
        }
    }

    private void addPointsToOpponent(int points) {
        if (currentRound.getValue() == 1) {
            player2Score.setValue(player2Score.getValue() + points);
        } else {
            player1Score.setValue(player1Score.getValue() + points);
        }
    }

    private void advanceRound() {
        if (currentRound.getValue() < 2) {
            currentRound.setValue(2);
            currentStep.setValue(0);
            gameState.setValue(GameState.ROUND_OVER);
        } else {
            gameState.setValue(GameState.GAME_OVER);
        }
    }

    public void startNextRound() {
        loadQuestion();
    }

    public void skipToRound2() {
        currentRound.setValue(2);
        loadQuestion();
    }

    public void loadQuestionByIdForSteal(String id) {
        gameState.setValue(GameState.LOADING);
        repository.getQuestionById(id, new RepositoryCallback<KorakPoKorakQuestion>() {
            @Override
            public void onSuccess(KorakPoKorakQuestion q) {
                stealQuestion.setValue(q);
                gameState.setValue(GameState.STEAL_ATTEMPT);
            }
            @Override
            public void onFailure(Exception e) {
                loadQuestion();
            }
        });
    }

    public void submitStealAttempt(String answer) {
        if (gameState.getValue() != GameState.STEAL_ATTEMPT) return;
        KorakPoKorakQuestion sq = stealQuestion.getValue();
        if (sq != null && !answer.isEmpty() && isCorrect(answer, sq)) {
            player2Score.setValue((player2Score.getValue() != null ? player2Score.getValue() : 0) + BONUS_POINTS);
        }
        loadQuestion();
    }

    public void startSoloSteal(String questionId) {
        gameState.setValue(GameState.LOADING);
        repository.getQuestionById(questionId, new RepositoryCallback<KorakPoKorakQuestion>() {
            @Override
            public void onSuccess(KorakPoKorakQuestion q) {
                stealQuestion.setValue(q);
                gameState.setValue(GameState.SOLO_STEAL);
            }
            @Override
            public void onFailure(Exception e) {
                soloStealBonus.setValue(0);
                gameState.setValue(GameState.SOLO_STEAL_DONE);
            }
        });
    }

    public void submitSoloSteal(String answer) {
        if (gameState.getValue() != GameState.SOLO_STEAL) return;
        KorakPoKorakQuestion sq = stealQuestion.getValue();
        int bonus = 0;
        if (sq != null && !answer.isEmpty() && isCorrect(answer, sq)) {
            bonus = BONUS_POINTS;
        }
        soloStealBonus.setValue(bonus);
        gameState.setValue(GameState.SOLO_STEAL_DONE);
    }
}
