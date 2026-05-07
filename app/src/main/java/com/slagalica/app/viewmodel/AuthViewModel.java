package com.slagalica.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseUser;
import com.slagalica.app.repository.RepositoryCallback;
import com.slagalica.app.repository.UserRepository;

public class AuthViewModel extends ViewModel {

    private final UserRepository userRepository;

    private final MutableLiveData<FirebaseUser> currentUser = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> registrationSuccess = new MutableLiveData<>();
    private final MutableLiveData<Boolean> passwordChangeSuccess = new MutableLiveData<>();

    public AuthViewModel() {
        userRepository = new UserRepository();
    }

    public LiveData<FirebaseUser> getCurrentUser() { return currentUser; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<Boolean> getRegistrationSuccess() { return registrationSuccess; }
    public LiveData<Boolean> getPasswordChangeSuccess() { return passwordChangeSuccess; }

    public void register(String email, String username, String region, String password) {
        loading.setValue(true);
        userRepository.register(email, username, region, password, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loading.setValue(false);
                registrationSuccess.setValue(true);
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                errorMessage.setValue(e.getMessage());
            }
        });
    }

    public void login(String emailOrUsername, String password) {
        loading.setValue(true);
        boolean isEmail = emailOrUsername.contains("@");

        if (isEmail) {
            userRepository.loginWithEmail(emailOrUsername, password, loginCallback);
        } else {
            userRepository.loginWithUsername(emailOrUsername, password, loginCallback);
        }
    }

    private final RepositoryCallback<FirebaseUser> loginCallback = new RepositoryCallback<FirebaseUser>() {
        @Override
        public void onSuccess(FirebaseUser user) {
            loading.setValue(false);
            currentUser.setValue(user);
        }

        @Override
        public void onFailure(Exception e) {
            loading.setValue(false);
            errorMessage.setValue(e.getMessage());
        }
    };

    public void loginAsGuest() {
        loading.setValue(true);
        userRepository.loginAsGuest(new RepositoryCallback<FirebaseUser>() {
            @Override
            public void onSuccess(FirebaseUser user) {
                loading.setValue(false);
                currentUser.setValue(user);
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                errorMessage.setValue(e.getMessage());
            }
        });
    }

    public void changePassword(String oldPassword, String newPassword) {
        loading.setValue(true);
        userRepository.changePassword(oldPassword, newPassword, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loading.setValue(false);
                passwordChangeSuccess.setValue(true);
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                errorMessage.setValue(e.getMessage());
            }
        });
    }

    public void logout() {
        userRepository.logout();
    }

    public boolean isLoggedIn() {
        return userRepository.getCurrentUser() != null;
    }
}
