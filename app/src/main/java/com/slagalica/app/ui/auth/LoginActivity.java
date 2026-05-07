package com.slagalica.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.slagalica.app.R;
import com.slagalica.app.ui.HomeActivity;
import com.slagalica.app.util.GameToast;
import com.slagalica.app.viewmodel.AuthViewModel;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmailOrUsername, etPassword;
    private MaterialButton btnLogin;
    private ProgressBar progressBar;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setupViewModel();
        setupListeners();
    }

    private void initViews() {
        etEmailOrUsername = findViewById(R.id.etEmailOrUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.selectTab(tabLayout.getTabAt(0));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull TabLayout.Tab tab) {
                if (tab.getPosition() == 1) {
                    startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
                    finish();
                    overridePendingTransition(0, 0);
                }
            }

            @Override
            public void onTabUnselected(@NonNull TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(@NonNull TabLayout.Tab tab) {}
        });
    }

    private void setupViewModel() {
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        authViewModel.getLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnLogin.setEnabled(!isLoading);
        });

        authViewModel.getCurrentUser().observe(this, user -> {
            if (user != null) {
                startActivity(new Intent(this, HomeActivity.class));
                finish();
            }
        });

        authViewModel.getErrorMessage().observe(this, error -> {
            if (error != null) GameToast.show(this, error, GameToast.Type.ERROR);
        });
    }

    private void setupListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        findViewById(R.id.btnGuest).setOnClickListener(v -> authViewModel.loginAsGuest());
    }

    private void attemptLogin() {
        String emailOrUsername = etEmailOrUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (emailOrUsername.isEmpty() || password.isEmpty()) {
            GameToast.show(this, "All fields are required.", GameToast.Type.ERROR);
            return;
        }

        authViewModel.login(emailOrUsername, password);
    }
}
