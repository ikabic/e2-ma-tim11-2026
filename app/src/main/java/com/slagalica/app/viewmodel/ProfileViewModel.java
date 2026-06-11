package com.slagalica.app.viewmodel;

import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.slagalica.app.model.PlayerStatistics;
import com.slagalica.app.model.Profile;
import com.slagalica.app.model.User;
import com.slagalica.app.repository.ProfileRepository;
import com.slagalica.app.repository.RepositoryCallback;
import com.slagalica.app.repository.StatisticsRepository;

public class ProfileViewModel extends ViewModel {

    private final ProfileRepository profileRepository;
    private final StatisticsRepository statsRepository = new StatisticsRepository();

    private final MutableLiveData<User> user = new MutableLiveData<>();
    private final MutableLiveData<Profile> profile = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<PlayerStatistics> playerStats = new MutableLiveData<>();

    public ProfileViewModel() {
        profileRepository = new ProfileRepository();
    }

    public LiveData<User> getUser() { return user; }
    public LiveData<Profile> getProfile() { return profile; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<PlayerStatistics> getPlayerStats() { return playerStats; }

    public void loadProfile(String uid) {
        user.setValue(null);
        profile.setValue(null);
        loading.setValue(true);
        profileRepository.getProfile(uid, new RepositoryCallback<>() {
            @Override
            public void onSuccess(Pair<User, Profile> result) {
                user.setValue(result.first);
                profile.setValue(result.second);
                loading.setValue(false);
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                errorMessage.setValue(e.getMessage());
            }
        });
    }

    public void loadPlayerStats(String uid) {
        statsRepository.loadStats(uid, new RepositoryCallback<>() {
            @Override
            public void onSuccess(PlayerStatistics result) {
                playerStats.setValue(result);
            }
            @Override
            public void onFailure(Exception e) {
                errorMessage.setValue("Failed to load statistics: " + e.getMessage());
            }
        });
    }

    public void updateAvatar(String avatarUrl) {
        profileRepository.updateAvatar(avatarUrl, new RepositoryCallback<>() {
            @Override public void onSuccess(Void result) { loadProfile(null); }
            @Override public void onFailure(Exception e) { errorMessage.setValue("Failed to save avatar: " + e.getMessage()); }
        });
    }
}