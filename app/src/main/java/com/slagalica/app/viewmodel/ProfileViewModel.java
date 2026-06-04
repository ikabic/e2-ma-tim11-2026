package com.slagalica.app.viewmodel;

import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.slagalica.app.model.Profile;
import com.slagalica.app.model.User;
import com.slagalica.app.repository.ProfileRepository;
import com.slagalica.app.repository.RepositoryCallback;

public class ProfileViewModel extends ViewModel {

    private final ProfileRepository profileRepository;

    private final MutableLiveData<User> user = new MutableLiveData<>();
    private final MutableLiveData<Profile> profile = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    public ProfileViewModel() {
        profileRepository = new ProfileRepository();
    }

    public LiveData<User> getUser() { return user; }
    public LiveData<Profile> getProfile() { return profile; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getLoading() { return loading; }

    public void loadProfile() {
        loading.setValue(true);
        profileRepository.getProfile(new RepositoryCallback<Pair<User, Profile>>() {
            @Override
            public void onSuccess(Pair<User, Profile> result) {
                user.setValue(result.first);
                profile.setValue(result.second);
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                errorMessage.setValue(e.getMessage());
            }
        });
    }

    public void updateAvatar(String avatarUrl) {
        profileRepository.updateAvatar(avatarUrl, new RepositoryCallback<>() {
            @Override public void onSuccess(Void result) { loadProfile(); }
            @Override public void onFailure(Exception e) { errorMessage.setValue("Failed to save avatar: " + e.getMessage()); }
        });
    }
}