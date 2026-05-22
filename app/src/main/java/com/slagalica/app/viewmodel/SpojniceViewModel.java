package com.slagalica.app.viewmodel;

import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.slagalica.app.model.SpojniceQuestion;
import com.slagalica.app.repository.RepositoryCallback;
import com.slagalica.app.repository.SpojniceRepository;

import java.util.List;

public class SpojniceViewModel extends ViewModel {

    private final SpojniceRepository repository;

    private final MutableLiveData<List<SpojniceQuestion>> questions = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(0);

    private final MutableLiveData<Integer> p1Score = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> p2Score = new MutableLiveData<>(0);

    private final MutableLiveData<Long> timeLeft = new MutableLiveData<>(5000L);
    private CountDownTimer countDownTimer;
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    //private SpojniceAnswer p1Answer;
    //private SpojniceAnswer p2Answer;

    public SpojniceViewModel() {
        repository = new SpojniceRepository();
        loadQuestions();
    }

    private final MutableLiveData<Boolean> gameOver = new MutableLiveData<>(false);

    public LiveData<List<SpojniceQuestion>> getQuestions() { return questions; }
    public LiveData<Integer> getP1Score() { return p1Score; }
    public LiveData<Integer> getP2Score() { return p2Score; }
    public LiveData<Integer> getCurrentIndex() { return currentIndex; }
    public LiveData<Long> getTimeLeft()  { return timeLeft; }
    public LiveData<Boolean> getGameOver() { return gameOver; }

    private void loadQuestions() {
        repository.getRandomQuestions(new RepositoryCallback<List<SpojniceQuestion>>() {
            @Override
            public void onSuccess(List<SpojniceQuestion> result) {
                questions.setValue(result);
                currentIndex.setValue(0);
            }

            @Override
            public void onFailure(Exception e) {
                errorMessage.setValue(e.getMessage());
            }
        });
    }

    public void startQuestion() {
        //p1Answer = null;
        //p2Answer = null;

        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(30000, 100) {
            @Override public void onTick(long ms) { timeLeft.setValue(ms); }
            @Override public void onFinish() { timeLeft.setValue(0L); finishQuestion(); }
        }.start();
    }

    public void submitAnswer(int player, String questionId, int answerIndex) {

    }

    public void finishQuestion() {
        SpojniceQuestion q = questions.getValue().get(currentIndex.getValue());

        //boolean p1Correct = p1Answer != null && p1Answer.getAnswerIndex() == q.getCorrectAnswerIndex();
        //boolean p2Correct = p2Answer != null && p2Answer.getAnswerIndex() == q.getCorrectAnswerIndex();

        int nextIndex = currentIndex.getValue() + 1;
        if (nextIndex >= 2) {
            gameOver.setValue(true);
        } else {
            currentIndex.setValue(nextIndex);
        }
    }
}