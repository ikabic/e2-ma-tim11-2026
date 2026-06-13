package com.slagalica.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.ValueEventListener;
import com.slagalica.app.model.Challenge;
import com.slagalica.app.repository.ChallengeRepository;
import com.slagalica.app.repository.RepositoryCallback;

import java.util.List;

public class ChallengeViewModel extends ViewModel {

    private final ChallengeRepository repository = new ChallengeRepository();

    private final MutableLiveData<List<Challenge>> openChallenges = new MutableLiveData<>();
    private final MutableLiveData<ChallengeRepository.ChallengeState> currentChallenge = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> createdChallengeId = new MutableLiveData<>();
    private final MutableLiveData<String> joinedChallengeId = new MutableLiveData<>();

    private ValueEventListener openListener;
    private String openRegion;
    private ValueEventListener challengeListener;
    private String listenedChallengeId;

    public LiveData<List<Challenge>> getOpenChallenges() { return openChallenges; }
    public LiveData<ChallengeRepository.ChallengeState> getCurrentChallenge() { return currentChallenge; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<String> getCreatedChallengeId() { return createdChallengeId; }
    public LiveData<String> getJoinedChallengeId() { return joinedChallengeId; }

    public String getUid() { return repository.getUid(); }

    public void startListeningOpen(String region) {
        stopListeningOpen();
        openRegion = region;
        openListener = repository.listenForOpenChallenges(region, openChallenges::postValue);
    }

    public void stopListeningOpen() {
        if (openListener != null && openRegion != null) {
            repository.removeOpenChallengesListener(openRegion, openListener);
            openListener = null;
        }
    }

    public void createChallenge(String region, String username, int stars, int tokens) {
        repository.createChallenge(region, username, stars, tokens, new RepositoryCallback<String>() {
            @Override public void onSuccess(String id) { createdChallengeId.postValue(id); }
            @Override public void onFailure(Exception e) { errorMessage.postValue(e.getMessage()); }
        });
    }

    public void clearCreatedChallengeId() {
        createdChallengeId.setValue(null);
    }

    public void joinChallenge(String challengeId, String username) {
        repository.joinChallenge(challengeId, username, new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void v) { joinedChallengeId.postValue(challengeId); }
            @Override public void onFailure(Exception e) {
                String m = e.getMessage();
                if (m != null && m.contains("Already joined")) {
                    joinedChallengeId.postValue(challengeId);
                } else {
                    errorMessage.postValue(m);
                }
            }
        });
    }

    public void clearJoinedChallengeId() {
        joinedChallengeId.setValue(null);
    }

    public void startListeningChallenge(String challengeId) {
        stopListeningChallenge();
        listenedChallengeId = challengeId;
        challengeListener = repository.listenForChallenge(challengeId, currentChallenge::postValue);
    }

    public void stopListeningChallenge() {
        if (challengeListener != null && listenedChallengeId != null) {
            repository.removeChallengeListener(listenedChallengeId, challengeListener);
            challengeListener = null;
        }
    }

    public void startChallenge(String challengeId) {
        repository.setChallengePlaying(challengeId);
    }

    public void submitScore(String challengeId, int score, Runnable onDone) {
        repository.submitScore(challengeId, score, new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void v) { if (onDone != null) onDone.run(); }
            @Override public void onFailure(Exception e) { errorMessage.postValue(e.getMessage()); }
        });
    }

    public void resolveIfReady(String challengeId) {
        repository.resolveAndPayout(challengeId, new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void v) {}
            @Override public void onFailure(Exception e) {}
        });
    }

    @Override
    protected void onCleared() {
        stopListeningOpen();
        stopListeningChallenge();
    }
}
