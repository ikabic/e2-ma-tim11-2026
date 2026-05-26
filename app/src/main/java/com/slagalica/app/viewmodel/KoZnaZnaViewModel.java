package com.slagalica.app.viewmodel;

import android.os.CountDownTimer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ValueEventListener;
import com.slagalica.app.model.KoZnaZnaQuestion;
import com.slagalica.app.repository.KoZnaZnaRepository;
import com.slagalica.app.repository.MatchRepository;
import com.slagalica.app.repository.RepositoryCallback;

import java.util.ArrayList;
import java.util.List;

public class KoZnaZnaViewModel extends ViewModel {

    private static final int  QUESTION_COUNT = 5;
    private static final long QUESTION_MS = 5000L;
    private static final long OPPONENT_WAIT_TIMEOUT_MS = 20000L;

    private final KoZnaZnaRepository kzzRepo = new KoZnaZnaRepository();
    private final MatchRepository matchRepo = new MatchRepository();

    private boolean isMatchGame = false;
    private boolean isPlayer1 = true;
    private String matchId = null;

    private final MutableLiveData<List<KoZnaZnaQuestion>> questions = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentIndex = new MutableLiveData<>(0);
    private final MutableLiveData<Long> timeLeft = new MutableLiveData<>(QUESTION_MS);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<int[]> questionResult = new MutableLiveData<>();
    private final MutableLiveData<Integer> myRunningScore = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> opponentRunningScore = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> myScoreReady = new MutableLiveData<>();
    private final MutableLiveData<int[]> finalScores = new MutableLiveData<>();
    private final MutableLiveData<Boolean> waitingForOpponent = new MutableLiveData<>(null);
    private final MutableLiveData<Integer> speedLostPoints = new MutableLiveData<>();

    private CountDownTimer countDownTimer;
    private CountDownTimer opponentWaitTimer;
    private long questionStartTime;
    private boolean answerSubmittedThisQuestion = false;

    private List<KoZnaZnaQuestion> allFetchedQuestions;

    private ValueEventListener questionIdsListener;
    private ValueEventListener opponentReadyListener;
    private ValueEventListener opponentAnswersListener;
    private ValueEventListener opponentScoreListener;

    public void initMatchMode(String matchId, boolean isPlayer1) {
        this.isMatchGame = true;
        this.isPlayer1 = isPlayer1;
        this.matchId = matchId;

        kzzRepo.initMatch(matchId);

        if (isPlayer1) matchRepo.writeKzzOrCnnStarted(matchId, "0");

        opponentScoreListener = kzzRepo.listenForOpponentScore(isPlayer1,
                new RepositoryCallback<Integer>() {
                    @Override public void onSuccess(Integer score) {
                        opponentRunningScore.postValue(score);
                    }
                    @Override public void onFailure(Exception e) { /* ignore */ }
                });

        loadQuestions();
    }

    public void loadQuestionsForSolo() {
        if (!isMatchGame) loadQuestions();
    }

    private void loadQuestions() {
        kzzRepo.getRandomQuestions(new RepositoryCallback<List<KoZnaZnaQuestion>>() {
            @Override
            public void onSuccess(List<KoZnaZnaQuestion> all) {
                allFetchedQuestions = all;

                if (!isMatchGame) {
                    applyQuestions(all.subList(0, Math.min(QUESTION_COUNT, all.size())));
                    return;
                }

                if (isPlayer1) {
                    List<KoZnaZnaQuestion> picked = new ArrayList<>(all.subList(0, Math.min(QUESTION_COUNT, all.size())));
                    List<String> ids = new ArrayList<>();
                    for (KoZnaZnaQuestion q : picked) ids.add(q.getId());
                    kzzRepo.writeQuestionIds(ids);
                    applyQuestions(picked);
                } else {
                    questionIdsListener = kzzRepo.listenForQuestionIds(
                            new RepositoryCallback<List<String>>() {
                                @Override
                                public void onSuccess(List<String> ids) {
                                    questionIdsListener = null;
                                    kzzRepo.getQuestionsByIds(ids,
                                            new RepositoryCallback<List<KoZnaZnaQuestion>>() {
                                                @Override
                                                public void onSuccess(List<KoZnaZnaQuestion> ordered) {
                                                    applyQuestions(ordered);
                                                }
                                                @Override
                                                public void onFailure(Exception e) {
                                                    applyQuestions(allFetchedQuestions.subList(0, Math.min(QUESTION_COUNT, allFetchedQuestions.size())));
                                                }
                                            });
                                }
                                @Override
                                public void onFailure(Exception e) {
                                    applyQuestions(allFetchedQuestions.subList(0, Math.min(QUESTION_COUNT, allFetchedQuestions.size())));
                                }
                            });
                }
            }

            @Override
            public void onFailure(Exception e) {
                errorMessage.setValue(e.getMessage());
            }
        });
    }

    private void applyQuestions(List<KoZnaZnaQuestion> list) {
        questions.postValue(list);
        currentIndex.postValue(0);
    }

    public LiveData<List<KoZnaZnaQuestion>> getQuestions() { return questions; }
    public LiveData<Integer> getCurrentIndex() { return currentIndex; }
    public LiveData<Long> getTimeLeft() { return timeLeft; }
    public LiveData<int[]> getQuestionResult() { return questionResult; }
    public LiveData<Integer> getMyRunningScore() { return myRunningScore; }
    public LiveData<Integer> getOpponentRunningScore() { return opponentRunningScore; }
    public LiveData<Integer> getMyScoreReady() { return myScoreReady; }
    public LiveData<int[]> getFinalScores() { return finalScores; }

    public void startQuestion() {
        questionStartTime = System.currentTimeMillis();
        answerSubmittedThisQuestion = false;

        cancelTimer();
        countDownTimer = new CountDownTimer(QUESTION_MS, 100) {
            @Override public void onTick(long ms)  { timeLeft.setValue(ms); }
            @Override public void onFinish() {
                timeLeft.setValue(0L);
                submitAnswer(-1, 0);
            }
        }.start();
    }

    public void submitAnswer(int answerIndex, int skipped) {
        if (answerSubmittedThisQuestion) return;
        answerSubmittedThisQuestion = true;
        cancelTimer();

        int idx = currentIndex.getValue() != null ? currentIndex.getValue() : 0;
        long elapsed = System.currentTimeMillis() - questionStartTime;

        List<KoZnaZnaQuestion> qs = questions.getValue();
        int correct = (qs != null) ? qs.get(idx).getCorrectAnswerIndex() : -1;
        boolean correctAnswer = (answerIndex != -1 && answerIndex == correct);

        if (isMatchGame) {
            kzzRepo.writeAnswer(isPlayer1, idx, answerIndex, elapsed);

            if (!correctAnswer) {
                int delta = (answerIndex == -1) ? 0 : -5;
                applyFinalDelta(idx, delta, correct, answerIndex, skipped, false);
            } else
                readOpponentAnswerOnceThenScore(idx, correct, answerIndex, elapsed, skipped);
        } else {
            int delta = (answerIndex == -1) ? 0 : (correctAnswer ? 10 : -5);
            applyFinalDelta(idx, delta, correct, answerIndex, skipped, false);
        }
    }

    private void readOpponentAnswerOnceThenScore(int idx, int correct, int myAnswer, long myElapsed, int skipped) {
        kzzRepo.readOpponentAnswerOnce(isPlayer1, idx, new RepositoryCallback<long[]>() {
            @Override
            public void onSuccess(long[] oppData) {
                int oppAnswer = (int) oppData[0];
                long oppElapsed = oppData[1];
                boolean oppCorrect = (oppAnswer != -1 && oppAnswer == correct);

                if (!oppCorrect)
                    applyFinalDelta(idx, 10, correct, myAnswer, skipped, false);
                else {
                    boolean faster = myElapsed <= oppElapsed;
                    if (faster)
                        applyFinalDelta(idx, 10, correct, myAnswer, skipped, false);
                    else {
                        speedLostPoints.postValue(idx);
                        applyFinalDelta(idx, 0, correct, myAnswer, skipped, true);
                    }
                }
            }
            @Override
            public void onFailure(Exception e) {
                applyFinalDelta(idx, 10, correct, myAnswer, skipped, false);
            }
        });
    }

    private void applyFinalDelta(int idx, int delta, int correct, int answerIndex, int skipped, boolean lostToSpeed) {
        int current  = myRunningScore.getValue() != null ? myRunningScore.getValue() : 0;
        int newScore = clamp(current + delta, -25, 50);
        myRunningScore.postValue(newScore);

        questionResult.postValue(new int[]{delta, correct, answerIndex, skipped, lostToSpeed ? 1 : 0});

        if (isMatchGame) kzzRepo.writeRunningScore(isPlayer1, newScore);
    }

    public void advanceOrFinish() {
        int idx = currentIndex.getValue() != null ? currentIndex.getValue() : 0;
        int next = idx + 1;

        if (next < QUESTION_COUNT) currentIndex.setValue(next);
        else finishMyGame();
    }

    private void finishMyGame() {
        if (opponentAnswersListener != null) {
            kzzRepo.removeOpponentAnswersListener(opponentAnswersListener);
            opponentAnswersListener = null;
        }

        int myScore = myRunningScore.getValue() != null ? myRunningScore.getValue() : 0;

        if (!isMatchGame) {
            finalScores.setValue(isPlayer1 ? new int[]{myScore, 0} : new int[]{0, myScore});
            return;
        }

        myScoreReady.postValue(myScore);
        kzzRepo.writeDone(isPlayer1);
        waitingForOpponent.setValue(true);

        opponentReadyListener = kzzRepo.listenForOpponentReady(isPlayer1, this::resolveScores);

        opponentWaitTimer = new CountDownTimer(OPPONENT_WAIT_TIMEOUT_MS, OPPONENT_WAIT_TIMEOUT_MS) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() {
                if (opponentReadyListener != null) {
                    kzzRepo.removeOpponentReadyListener(opponentReadyListener);
                    opponentReadyListener = null;
                }
                waitingForOpponent.postValue(false);
            }
        }.start();
    }

    private void resolveScores() {
        cancelOpponentWaitTimer();
        if (opponentReadyListener != null) {
            kzzRepo.removeOpponentReadyListener(opponentReadyListener);
            opponentReadyListener = null;
        }
        kzzRepo.readAllAnswers(new RepositoryCallback<DataSnapshot>() {
            @Override public void onSuccess(DataSnapshot snap) {
                waitingForOpponent.postValue(false);
                commitFinalScores(snap);
            }
            @Override public void onFailure(Exception e) {
                waitingForOpponent.postValue(false);
            }
        });
    }

    private void commitFinalScores(DataSnapshot snap) {
        List<KoZnaZnaQuestion> qs = questions.getValue();
        int p1Correct = 0, p1Wrong = 0, p2Correct = 0, p2Wrong = 0;
        if (qs != null) {
            for (int i = 0; i < QUESTION_COUNT; i++) {
                int correct = qs.get(i).getCorrectAnswerIndex();
                int p1A = getInt(snap, "p1Answer" + i, -1);
                int p2A = getInt(snap, "p2Answer" + i, -1);
                if (p1A != -1) { if (p1A == correct) p1Correct++; else p1Wrong++; }
                if (p2A != -1) { if (p2A == correct) p2Correct++; else p2Wrong++; }
            }
        }

        int p1Score = isPlayer1 ? myRunningScore.getValue() : opponentRunningScore.getValue();
        int p2Score = isPlayer1 ? opponentRunningScore.getValue() : myRunningScore.getValue();
        finalScores.postValue(new int[]{ p1Score, p2Score });

        kzzRepo.writeStats(p1Correct, p1Wrong, p2Correct, p2Wrong, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void v) { kzzRepo.cleanupMatchData(); }
            @Override
            public void onFailure(Exception e) { kzzRepo.cleanupMatchData(); }
        });
    }

    public void writeForfeit() {
        if (!isMatchGame || matchRepo == null) return;
        String uid = matchRepo.getUid();
        if (uid != null) matchRepo.writeForfeit(matchId, uid);
    }

    private void cancelTimer() {
        if (countDownTimer != null) { countDownTimer.cancel(); countDownTimer = null; }
    }

    private void cancelOpponentWaitTimer() {
        if (opponentWaitTimer != null) { opponentWaitTimer.cancel(); opponentWaitTimer = null; }
    }

    private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private int getInt(DataSnapshot snap, String key, int fallback) {
        Long v = snap.child(key).getValue(Long.class);
        return v != null ? v.intValue() : fallback;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelTimer();
        cancelOpponentWaitTimer();
        if (questionIdsListener != null) kzzRepo.removeQuestionIdsListener(questionIdsListener);
        if (opponentReadyListener != null) kzzRepo.removeOpponentReadyListener(opponentReadyListener);
        if (opponentAnswersListener != null) kzzRepo.removeOpponentAnswersListener(opponentAnswersListener);
        if (opponentScoreListener != null) kzzRepo.removeOpponentScoreListener(opponentScoreListener);
    }
}