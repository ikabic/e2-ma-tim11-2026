package com.slagalica.app.viewmodel;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.ValueEventListener;
import com.slagalica.app.model.SpojniceQuestion;
import com.slagalica.app.repository.MatchRepository;
import com.slagalica.app.repository.RepositoryCallback;
import com.slagalica.app.repository.SpojniceRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpojniceViewModel extends ViewModel {

    private static final int ROUND_COUNT = 2;
    private static final long ROUND_MS = 30000L;
    private static final long OPPONENT_WAIT_MS = 35000L;

    private final SpojniceRepository spojRepo  = new SpojniceRepository();
    private final MatchRepository matchRepo = new MatchRepository();

    private boolean isMatchGame = false;
    private boolean isPlayer1 = true;
    private String matchId = null;
    private boolean isSoloMatch = false;

    private final MutableLiveData<List<SpojniceQuestion>> questions = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentRound = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> myActiveTurn = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> activeLeftIdx = new MutableLiveData<>(-1);
    private final MutableLiveData<int[]> answerResult = new MutableLiveData<>();
    private final MutableLiveData<Map<Integer, Integer>> confirmedPairs = new MutableLiveData<>();
    private final MutableLiveData<Long> timeLeft = new MutableLiveData<>(ROUND_MS);
    private final MutableLiveData<Integer> myRunningScore = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> opponentRunningScore = new MutableLiveData<>(0);
    private final MutableLiveData<int[]> opponentAnswerResult = new MutableLiveData<>();
    private final MutableLiveData<Integer> opponentActiveLeftIdx = new MutableLiveData<>(-1);
    private final MutableLiveData<Boolean> waitingForOpp = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> opponentLeft = new MutableLiveData<>(false);
    private final MutableLiveData<int[]> finalScores = new MutableLiveData<>();
    private final MutableLiveData<Map<Integer, Integer>> revealedCorrectPairs = new MutableLiveData<>();
    private final MutableLiveData<Map<Integer, Boolean>> pairOwners = new MutableLiveData<>(new HashMap<>());

    private final Map<Integer, Integer> myPairs = new HashMap<>();
    private final Map<Integer, Integer> opponentPairs = new HashMap<>();

    private int prevMyScore = 0;
    private int prevOpponentScore = 0;

    private int p1TotalGuesses = 0;
    private int p2TotalGuesses = 0;

    private int activeLeft = 0;
    private boolean inLeftoverChance = false;
    private boolean leftoverAlreadyPlayed = false;
    private boolean isInRevealPhase = false;
    private boolean isWaitingForGameFinish = false;
    private int round1MyScore = 0;
    private int round1OpponentScore = 0;

    private CountDownTimer roundTimer;
    private CountDownTimer opponentWaitTimer;

    private ValueEventListener opponentScoreListener;
    private ValueEventListener opponentTurnDoneListener;
    private ValueEventListener opponentPairsListener;
    private ValueEventListener questionIdsListener;
    private ValueEventListener opponentGuessEventListener;
    private ValueEventListener opponentLeftIdxListener;
    private ValueEventListener forfeitListener;

    public void initMatchMode(String matchId, boolean isPlayer1) {
        this.isMatchGame = true;
        this.isPlayer1 = isPlayer1;
        this.matchId = matchId;

        spojRepo.initMatch(matchId);

        if (isPlayer1) matchRepo.writeKzzOrCnnStarted(matchId, "1");

        opponentScoreListener = spojRepo.listenForOpponentScore(isPlayer1,
                new RepositoryCallback<Integer>() {
                    @Override public void onSuccess(Integer score) {
                        opponentRunningScore.postValue(score + prevOpponentScore);
                    }
                    @Override public void onFailure(Exception e) { }
                });

        String myUid = matchRepo.getUid();
        if (myUid != null) forfeitListener = matchRepo.listenForForfeit(matchId, myUid, this::handleOpponentForfeit);

        loadQuestions();
    }

    public void initMatchSoloMode(String matchId, boolean isPlayer1) {
        this.isMatchGame = true;
        this.isPlayer1 = isPlayer1;
        this.matchId = matchId;
        this.isSoloMatch = true;
        loadQuestions();
    }

    private void handleOpponentForfeit() {
        cancelOpponentWaitTimer();
        opponentLeft.postValue(true);
        isSoloMatch = true;

        if (questions.getValue() == null) {
            if (questionIdsListener != null) {
                spojRepo.removeListener(questionIdsListener);
                questionIdsListener = null;
            }
            loadQuestions();
            return;
        }

        if (Boolean.TRUE.equals(waitingForOpp.getValue())) {
            waitingForOpp.postValue(false);

            if (opponentTurnDoneListener != null) {
                spojRepo.removeListener(opponentTurnDoneListener);
                opponentTurnDoneListener = null;

                if (isWaitingForGameFinish) {
                    isWaitingForGameFinish = false;
                    resolveAndFinish();
                } else {
                    int round = currentRound.getValue() != null ? currentRound.getValue() : 0;
                    boolean isOpponentPrimaryTurn = ((round == 0) != isPlayer1);

                    if (isOpponentPrimaryTurn && !leftoverAlreadyPlayed) {
                        leftoverAlreadyPlayed = true;
                        if (opponentPairsListener != null) {
                            spojRepo.removeListener(opponentPairsListener);
                            opponentPairsListener = null;
                        }
                        spojRepo.readOpponentPairs(isPlayer1, round, new RepositoryCallback<>() {
                            @Override
                            public void onSuccess(Map<Integer, Integer> oppPairs) {
                                opponentPairs.clear();
                                opponentPairs.putAll(oppPairs);
                                myPairs.clear();
                                confirmedPairs.postValue(getCombinedPairs());
                                startMyLeftoverChance(round);
                            }
                            @Override
                            public void onFailure(Exception e) { endRoundAndReveal(round); }
                        });
                    } else endRoundAndReveal(round);
                }
            }
        }
    }

    public void loadQuestionsForSolo() {
        if (!isMatchGame || isSoloMatch) loadQuestions();
    }

    private void loadQuestions() {
        String seed = (matchId != null) ? matchId : String.valueOf(System.currentTimeMillis());

        spojRepo.getRandomQuestions(seed, new RepositoryCallback<List<SpojniceQuestion>>() {
            @Override
            public void onSuccess(List<SpojniceQuestion> all) {
                if (!isMatchGame || isSoloMatch) {
                    applyQuestions(all);
                    return;
                }
                if (isPlayer1) {
                    List<String> ids = new ArrayList<>();
                    for (SpojniceQuestion q : all) ids.add(q.getId());
                    spojRepo.writeQuestionIds(ids);
                    applyQuestions(all);
                } else {
                    questionIdsListener = spojRepo.listenForQuestionIds(
                            new RepositoryCallback<List<String>>() {
                                @Override
                                public void onSuccess(List<String> ids) {
                                    questionIdsListener = null;
                                    spojRepo.getQuestionsByIds(ids, matchId,
                                            new RepositoryCallback<List<SpojniceQuestion>>() {
                                                @Override public void onSuccess(List<SpojniceQuestion> ordered) { applyQuestions(ordered); }
                                                @Override public void onFailure(Exception e) { applyQuestions(all); }
                                            });
                                }
                                @Override public void onFailure(Exception e) { applyQuestions(all); }
                            });
                }
            }
            @Override public void onFailure(Exception e) { }
        });
    }

    private void applyQuestions(List<SpojniceQuestion> list) {
        questions.postValue(list);
        startRound(0);
    }

    public LiveData<List<SpojniceQuestion>> getQuestions() { return questions; }
    public LiveData<Integer> getCurrentRound() { return currentRound; }
    public LiveData<Boolean> getMyActiveTurn() { return myActiveTurn; }
    public LiveData<Integer> getActiveLeftIdx() { return activeLeftIdx; }
    public LiveData<int[]> getAnswerResult() { return answerResult; }
    public LiveData<Map<Integer, Integer>> getConfirmedPairs() { return confirmedPairs; }
    public LiveData<Long> getTimeLeft() { return timeLeft; }
    public LiveData<Integer> getMyRunningScore() { return myRunningScore; }
    public LiveData<Integer> getOpponentRunningScore() { return opponentRunningScore; }
    public LiveData<Boolean> getWaitingForOpp() { return waitingForOpp; }
    public LiveData<int[]> getOpponentAnswerResult() { return opponentAnswerResult; }
    public LiveData<Boolean> getOpponentLeft() { return opponentLeft; }
    public LiveData<int[]> getFinalScores() { return finalScores; }
    public LiveData<Integer> getOpponentActiveLeftIdx() { return opponentActiveLeftIdx; }
    public LiveData<Map<Integer, Integer>> getRevealedCorrectPairs() { return revealedCorrectPairs; }
    public LiveData<Map<Integer, Boolean>> getPairOwners() { return pairOwners; }

    private void startRoundTimer() {
        cancelRoundTimer();
        roundTimer = new CountDownTimer(ROUND_MS, 100) {
            @Override public void onTick(long ms) { timeLeft.postValue(ms); }
            @Override public void onFinish() {
                timeLeft.postValue(0L);
                onTimerExpired();
            }
        }.start();
    }

    private void startRound(int round) {
        currentRound.postValue(round);
        isWaitingForGameFinish = false;

        if (round == 1) {
            round1MyScore = myPairs.size() * 2;
            round1OpponentScore = opponentPairs.size() * 2;
        }

        myPairs.clear();
        opponentPairs.clear();

        pairOwners.postValue(new HashMap<>());
        confirmedPairs.postValue(new HashMap<>());

        leftoverAlreadyPlayed = false;
        inLeftoverChance = false;

        activeLeft = 0;
        activeLeftIdx.postValue(activeLeft);

        if (opponentGuessEventListener != null) spojRepo.removeListener(opponentGuessEventListener);
        if (opponentLeftIdxListener != null) spojRepo.removeListener(opponentLeftIdxListener);
        opponentActiveLeftIdx.postValue(-1);

        boolean iAmPrimary = isSoloMatch || ((round == 0) == isPlayer1);

        if (iAmPrimary) {
            myActiveTurn.postValue(true);
            waitingForOpp.postValue(false);
            if (isMatchGame && !isSoloMatch) spojRepo.writeActiveLeftIdx(isPlayer1, 0);
            startRoundTimer();
        } else {
            myActiveTurn.postValue(false);
            waitingForOpp.postValue(true);
            listenForOpponentGuesses(round);
            listenForOpponentSubTurnDone(round);

            if (isMatchGame) {
                if (opponentGuessEventListener != null) spojRepo.removeListener(opponentGuessEventListener);
                opponentGuessEventListener = spojRepo.listenForOpponentGuesses(isPlayer1, new RepositoryCallback<Map<String, Object>>() {
                    @Override
                    public void onSuccess(Map<String, Object> data) {
                        int rIdx = (int) data.get("rightIdx");
                        boolean isCorrect = (boolean) data.get("isCorrect");
                        opponentAnswerResult.postValue(new int[]{rIdx, isCorrect ? 1 : 0});
                    }
                    @Override public void onFailure(Exception e) {}
                });

                if (opponentLeftIdxListener != null) spojRepo.removeListener(opponentLeftIdxListener);
                opponentLeftIdxListener = spojRepo.listenForOpponentActiveLeft(isPlayer1, new RepositoryCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer idx) { opponentActiveLeftIdx.postValue(idx); }
                    @Override public void onFailure(Exception e) {}
                });
            }
        }
    }

    public void tapRightTerm(int rightIdx) {
        if (isInRevealPhase) return;

        Boolean active = myActiveTurn.getValue();
        if (active == null || !active) return;
        if (myPairs.containsValue(rightIdx) || opponentPairs.containsValue(rightIdx)) return;

        if (isPlayer1) p1TotalGuesses++;
        else p2TotalGuesses++;

        int round = currentRound.getValue() != null ? currentRound.getValue() : 0;
        List<SpojniceQuestion> qs = questions.getValue();
        if (qs == null || round >= qs.size()) return;

        SpojniceQuestion q = qs.get(round);
        String leftTerm = q.getLeftTerms().get(activeLeft);
        String rightTerm = q.getRightTerms().get(rightIdx);

        Map<String, String> correct = q.getCorrectPairs();
        boolean isCorrect = rightTerm.equals(correct.get(leftTerm));

        int currentScore = round1MyScore + (myPairs.size() * 2);
        myRunningScore.postValue(currentScore + prevMyScore);

        if (isMatchGame && !isSoloMatch) spojRepo.writeLastGuess(isPlayer1, rightIdx, isCorrect);

        if (isCorrect) {
            myPairs.put(activeLeft, rightIdx);
            confirmedPairs.postValue(getCombinedPairs());

            Map<Integer, Boolean> owners = pairOwners.getValue();
            if (owners == null) owners = new HashMap<>();
            owners.put(activeLeft, isPlayer1);
            pairOwners.postValue(owners);

            int currentGameScore = round1MyScore + (myPairs.size() * 2);

            myRunningScore.postValue(currentGameScore + prevMyScore);

            if (isMatchGame && !isSoloMatch) {
                spojRepo.writeRunningScore(isPlayer1, currentGameScore);
                spojRepo.writePairs(isPlayer1, round, myPairs);
            }

            answerResult.postValue(new int[]{rightIdx, 1});
            advanceActiveLeft();
        } else {
            answerResult.postValue(new int[]{rightIdx, 0});
            advanceActiveLeft();
        }
    }

    private void listenForOpponentGuesses(int round) {
        if (opponentPairsListener != null) spojRepo.removeListener(opponentPairsListener);
        if (!isMatchGame) return;

        opponentPairsListener = spojRepo.listenForOpponentPairs(isPlayer1, round, new RepositoryCallback<Map<Integer, Integer>>() {
            @Override
            public void onSuccess(Map<Integer, Integer> oppPairs) {
                opponentPairs.clear();
                opponentPairs.putAll(oppPairs);
                confirmedPairs.postValue(getCombinedPairs());

                Map<Integer, Boolean> owners = pairOwners.getValue();
                if (owners == null) owners = new HashMap<>();

                boolean opponentIsP1 = !isPlayer1;

                for (Integer leftIdx : oppPairs.keySet())
                    owners.put(leftIdx, opponentIsP1);
                pairOwners.postValue(owners);
            }
            @Override
            public void onFailure(Exception e) {}
        });
    }

    private void advanceActiveLeft() {
        int round = currentRound.getValue() != null ? currentRound.getValue() : 0;
        List<SpojniceQuestion> qs = questions.getValue();
        if (qs == null) return;
        int termCount = qs.get(round).getLeftTerms().size();  // 5

        int next = activeLeft + 1;
        while (next < termCount && (myPairs.containsKey(next) || opponentPairs.containsKey(next))) next++;

        if (next >= termCount)
            onMySubTurnDone();
        else {
            activeLeft = next;
            activeLeftIdx.postValue(activeLeft);
            if (isMatchGame && !isSoloMatch) spojRepo.writeActiveLeftIdx(isPlayer1, activeLeft);
        }
    }

    private void onTimerExpired() {
        cancelRoundTimer();
        onMySubTurnDone();
    }

    private void onMySubTurnDone() {
        cancelRoundTimer();
        myActiveTurn.postValue(false);
        int round = currentRound.getValue() != null ? currentRound.getValue() : 0;

        persistMyPairs(round);

        if (!isMatchGame || isSoloMatch) {
            endRoundAndReveal(round);
            return;
        }

        spojRepo.writeSubTurnDone(isPlayer1, round, inLeftoverChance);

        if (inLeftoverChance) {
            endRoundAndReveal(round);
            return;
        }

        List<SpojniceQuestion> qs = questions.getValue();
        if (qs == null) { endRoundAndReveal(round); return; }
        int termCount = qs.get(round).getLeftTerms().size();
        boolean hasLeftovers = myPairs.size() < termCount;

        if (hasLeftovers) {
            waitingForOpp.postValue(true);
            listenForOpponentLeftoverDone(round);
        } else
            endRoundAndReveal(round);
    }

    private void listenForOpponentLeftoverDone(int round) {
        cancelOpponentWaitTimer();

        if (isMatchGame) {
            listenForOpponentGuesses(round);

            if (opponentGuessEventListener != null) spojRepo.removeListener(opponentGuessEventListener);
            opponentGuessEventListener = spojRepo.listenForOpponentGuesses(isPlayer1, new RepositoryCallback<Map<String, Object>>() {
                @Override
                public void onSuccess(Map<String, Object> data) {
                    int rIdx = (int) data.get("rightIdx");
                    boolean isCorrect = (boolean) data.get("isCorrect");
                    opponentAnswerResult.postValue(new int[]{rIdx, isCorrect ? 1 : 0});
                }
                @Override public void onFailure(Exception e) {}
            });

            if (opponentLeftIdxListener != null) spojRepo.removeListener(opponentLeftIdxListener);
            opponentLeftIdxListener = spojRepo.listenForOpponentActiveLeft(isPlayer1, new RepositoryCallback<Integer>() {
                @Override
                public void onSuccess(Integer idx) { opponentActiveLeftIdx.postValue(idx); }
                @Override public void onFailure(Exception e) {}
            });
        }

        opponentTurnDoneListener = spojRepo.listenForOpponentSubTurnDone(isPlayer1, round, true, () -> {
            opponentTurnDoneListener = null;
            cancelOpponentWaitTimer();
            waitingForOpp.postValue(false);
            endRoundAndReveal(round);
        });

        opponentWaitTimer = new CountDownTimer(OPPONENT_WAIT_MS, OPPONENT_WAIT_MS) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() {
                if (opponentTurnDoneListener != null) {
                    spojRepo.removeListener(opponentTurnDoneListener);
                    opponentTurnDoneListener = null;
                }
                waitingForOpp.postValue(false);
                opponentLeft.postValue(true);
                endRoundAndReveal(round);
            }
        }.start();
    }

    private void listenForOpponentSubTurnDone(int round) {
        cancelOpponentWaitTimer();

        opponentTurnDoneListener = spojRepo.listenForOpponentSubTurnDone(isPlayer1, round, false, () -> {
            opponentTurnDoneListener = null;
            cancelOpponentWaitTimer();
            waitingForOpp.postValue(false);

            if (opponentPairsListener != null) spojRepo.removeListener(opponentPairsListener);

            spojRepo.readOpponentPairs(isPlayer1, round, new RepositoryCallback<Map<Integer, Integer>>() {
                @Override
                public void onSuccess(Map<Integer, Integer> oppPairs) {
                    List<SpojniceQuestion> qs = questions.getValue();
                    if (qs == null) {
                        endRoundAndReveal(round);
                        return;
                    }

                    int totalTerms = qs.get(round).getLeftTerms().size();
                    boolean hasLeftovers = oppPairs.size() < totalTerms;

                    if (hasLeftovers && !leftoverAlreadyPlayed) {
                        leftoverAlreadyPlayed = true;

                        opponentPairs.clear();
                        opponentPairs.putAll(oppPairs);
                        myPairs.clear();

                        confirmedPairs.postValue(getCombinedPairs());
                        startMyLeftoverChance(round);
                    } else
                        endRoundAndReveal(round);
                }
                @Override
                public void onFailure(Exception e) { endRoundAndReveal(round); }
            });
        });

        opponentWaitTimer = new CountDownTimer(OPPONENT_WAIT_MS, OPPONENT_WAIT_MS) {
            @Override public void onTick(long ms) {}
            @Override
            public void onFinish() {
                if (opponentTurnDoneListener != null) spojRepo.removeListener(opponentTurnDoneListener);
                waitingForOpp.postValue(false);
                opponentLeft.postValue(true);
                endRoundAndReveal(round);
            }
        }.start();
    }

    private void startMyLeftoverChance(int round) {
        List<SpojniceQuestion> qs = questions.getValue();
        if (qs == null) {
            endRoundAndReveal(round);
            return;
        }
        int termCount = qs.get(round).getLeftTerms().size();

        int first = 0;
        while (first < termCount && (myPairs.containsKey(first) || opponentPairs.containsKey(first))) first++;

        if (first >= termCount) {
            endRoundAndReveal(round);
            return;
        }

        inLeftoverChance = true;

        activeLeft = first;
        activeLeftIdx.postValue(activeLeft);

        if (isMatchGame) spojRepo.writeActiveLeftIdx(isPlayer1, activeLeft);
        myActiveTurn.postValue(true);
        startRoundTimer();
    }

    private void endRoundAndReveal(int round) {
        List<SpojniceQuestion> qs = questions.getValue();
        if (qs != null && round < qs.size()) {
            isInRevealPhase = true;
            SpojniceQuestion q = qs.get(round);
            List<String> currentLeft = q.getLeftTerms();
            List<String> currentRight = q.getRightTerms();
            Map<String, String> correctPairs = q.getCorrectPairs();

            Map<Integer, Integer> fullCorrectMapping = new HashMap<>();

            for (int l = 0; l < currentLeft.size(); l++) {
                String leftTerm = currentLeft.get(l);
                String targetRightTerm = correctPairs.get(leftTerm);
                int rIdx = currentRight.indexOf(targetRightTerm);
                if (rIdx != -1)
                    fullCorrectMapping.put(l, rIdx);
            }

            revealedCorrectPairs.postValue(fullCorrectMapping);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isInRevealPhase = false;
            revealedCorrectPairs.postValue(null);

            int nextRound = round + 1;
            if (nextRound < ROUND_COUNT)
                startRound(nextRound);
            else
                finishGame();
        }, 4000L);
    }

    private void finishGame() {
        int myCumulative = myRunningScore.getValue() != null ? myRunningScore.getValue() : prevMyScore;

        if (!isMatchGame || isSoloMatch) {
            finalScores.postValue(isPlayer1 ? new int[]{myCumulative, 0} : new int[]{0, myCumulative});
            return;
        }

        spojRepo.getScoreRef().child("data").child(isPlayer1 ? "p1GuessesCount" : "p2GuessesCount").setValue(isPlayer1 ? p1TotalGuesses : p2TotalGuesses);

        spojRepo.writeDone(isPlayer1);
        waitingForOpp.postValue(true);
        isWaitingForGameFinish = true;

        opponentTurnDoneListener = spojRepo.listenForOpponentDone(isPlayer1, () -> {
            opponentTurnDoneListener = null;
            cancelOpponentWaitTimer();
            waitingForOpp.postValue(false);
            resolveAndFinish();
        });

        opponentWaitTimer = new CountDownTimer(OPPONENT_WAIT_MS, OPPONENT_WAIT_MS) {
            @Override public void onTick(long ms) {}
            @Override public void onFinish() {
                if (opponentTurnDoneListener != null) {
                    spojRepo.removeListener(opponentTurnDoneListener);
                    opponentTurnDoneListener = null;
                }
                waitingForOpp.postValue(false);
                resolveAndFinish();
            }
        }.start();
    }

    private void resolveAndFinish() {
        if (spojRepo.getScoreRef() == null) {
            spojRepo.cleanupMatchData();
            return;
        }

        spojRepo.getScoreRef().get().addOnSuccessListener(new OnSuccessListener<DataSnapshot>() {
            @Override
            public void onSuccess(com.google.firebase.database.DataSnapshot snapshot) {
                Long p1ScoreLong = snapshot.child("p1").getValue(Long.class);
                Long p2ScoreLong = snapshot.child("p2").getValue(Long.class);

                int p1Score = p1ScoreLong != null ? p1ScoreLong.intValue() : 0;
                int p2Score = p2ScoreLong != null ? p2ScoreLong.intValue() : 0;

                int p1Correct = p1Score / 2;
                int p2Correct = p2Score / 2;

                Long p1GuessesLong = snapshot.child("data").child("p1GuessesCount").getValue(Long.class);
                Long p2GuessesLong = snapshot.child("data").child("p2GuessesCount").getValue(Long.class);

                int p1Guesses = p1GuessesLong != null ? p1GuessesLong.intValue() : p1TotalGuesses;
                int p2Guesses = p2GuessesLong != null ? p2GuessesLong.intValue() : p2TotalGuesses;

                if (p1Guesses < p1Correct) p1Guesses = p1Correct;
                if (p2Guesses < p2Correct) p2Guesses = p2Correct;

                finalScores.postValue(new int[]{ p1Score, p2Score });

                spojRepo.writeStats(p1Correct, p2Correct, p1Guesses, p2Guesses, new RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void v) { spojRepo.cleanupMatchData(); }
                    @Override
                    public void onFailure(Exception e) { spojRepo.cleanupMatchData(); }
                });
            }
        }).addOnFailureListener(e -> { spojRepo.cleanupMatchData(); });
    }

    private void persistMyPairs(int round) {
        if (!isMatchGame || isSoloMatch) return;

        Map<Integer, Integer> snapshot = new HashMap<>(myPairs);
        spojRepo.writePairs(isPlayer1, round, snapshot);
    }

    public void writeForfeit() {
        if (!isMatchGame || matchRepo == null) return;
        String uid = matchRepo.getUid();
        if (uid != null) matchRepo.writeForfeit(matchId, uid);
    }

    private Map<Integer, Integer> getCombinedPairs() {
        Map<Integer, Integer> combined = new HashMap<>(opponentPairs);
        combined.putAll(myPairs);
        return combined;
    }

    private void cancelRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    private void cancelOpponentWaitTimer() {
        if (opponentWaitTimer != null) {
            opponentWaitTimer.cancel();
            opponentWaitTimer = null;
        }
    }

    public void setPreviousScores(int myPrev, int oppPrev) {
        this.prevMyScore = myPrev;
        this.prevOpponentScore = oppPrev;

        this.myRunningScore.setValue(myPrev);
        this.opponentRunningScore.setValue(oppPrev);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelRoundTimer();
        cancelOpponentWaitTimer();
        if (questionIdsListener != null) spojRepo.removeListener(questionIdsListener);
        if (opponentTurnDoneListener != null) spojRepo.removeListener(opponentTurnDoneListener);
        if (opponentScoreListener != null) spojRepo.removeListener(opponentScoreListener);
        if (opponentPairsListener != null) spojRepo.removeListener(opponentPairsListener);
        if (opponentGuessEventListener != null) spojRepo.removeListener(opponentGuessEventListener);
        if (opponentLeftIdxListener != null) spojRepo.removeListener(opponentLeftIdxListener);
        if (forfeitListener != null) spojRepo.removeListener(forfeitListener);

        questionIdsListener = opponentTurnDoneListener = opponentScoreListener = opponentPairsListener = opponentGuessEventListener = opponentLeftIdxListener = forfeitListener = null;
    }
}