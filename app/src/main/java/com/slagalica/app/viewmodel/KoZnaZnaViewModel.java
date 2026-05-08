package com.slagalica.app.viewmodel;

import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.slagalica.app.model.KoZnaZnaAnswer;
import com.slagalica.app.model.KoZnaZnaQuestion;
import com.slagalica.app.repository.KoZnaZnaRepository;
import com.slagalica.app.repository.RepositoryCallback;

import java.util.List;

public class KoZnaZnaViewModel extends ViewModel {

    private final KoZnaZnaRepository repository;

    private final MutableLiveData<List<KoZnaZnaQuestion>> questions = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(0);

    private final MutableLiveData<Integer> p1Score = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> p2Score = new MutableLiveData<>(0);

    private final MutableLiveData<Long> timeLeft = new MutableLiveData<>(5000L);
    private CountDownTimer countDownTimer;
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private long questionStartTime;

    private KoZnaZnaAnswer p1Answer;
    private KoZnaZnaAnswer p2Answer;

    public KoZnaZnaViewModel() {
        repository = new KoZnaZnaRepository();
        loadQuestions();
    }

    public LiveData<List<KoZnaZnaQuestion>> getQuestions() { return questions; }
    public LiveData<Integer> getP1Score() { return p1Score; }
    public LiveData<Integer> getP2Score() { return p2Score; }
    public LiveData<Integer> getCurrentIndex() { return currentIndex; }
    public LiveData<Long> getTimeLeft()  { return timeLeft; }

    private void loadQuestions() {
        repository.getRandomQuestions(new RepositoryCallback<List<KoZnaZnaQuestion>>() {
            @Override
            public void onSuccess(List<KoZnaZnaQuestion> result) {
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
        questionStartTime = System.currentTimeMillis();
        p1Answer = null;
        p2Answer = null;

        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(5000, 100) {
            @Override public void onTick(long ms) { timeLeft.setValue(ms); }
            @Override public void onFinish() { timeLeft.setValue(0L); finishQuestion(); }
        }.start();
    }

    public void submitAnswer(int player, String questionId, int answerIndex) {
        long time = System.currentTimeMillis() - questionStartTime;
        KoZnaZnaAnswer answer = new KoZnaZnaAnswer(questionId, answerIndex, time);

        if (player == 1) p1Answer = answer;
        else p2Answer = answer;

        if (p1Answer != null && p2Answer != null) {
            if (countDownTimer != null) countDownTimer.cancel();
            finishQuestion();
        }
    }

    public void finishQuestion() {
        KoZnaZnaQuestion q = questions.getValue().get(currentIndex.getValue());

        boolean p1Correct = p1Answer != null && p1Answer.getAnswerIndex() == q.getCorrectAnswerIndex();
        boolean p2Correct = p2Answer != null && p2Answer.getAnswerIndex() == q.getCorrectAnswerIndex();

        int p1Delta = 0;
        int p2Delta = 0;

        if (p1Correct && p2Correct) {
            if (p1Answer.getTimestamp() < p2Answer.getTimestamp())
                p1Delta += 10;
            else
                p2Delta += 10;
        }

        else {
            if (p1Answer != null)
                p1Delta += p1Correct ? 10 : -5;
            if (p2Answer != null)
                p2Delta += p2Correct ? 10 : -5;
        }

        p1Score.setValue(Math.max(-25, Math.min(50, p1Score.getValue() + p1Delta)));
        p2Score.setValue(Math.max(-25, Math.min(50, p2Score.getValue() + p2Delta)));

        currentIndex.setValue(Math.min(currentIndex.getValue() + 1, 4));
    }
}