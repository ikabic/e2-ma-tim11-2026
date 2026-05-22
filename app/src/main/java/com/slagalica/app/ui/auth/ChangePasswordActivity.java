package com.slagalica.app.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import com.slagalica.app.BaseActivity;
import com.slagalica.app.util.GameToast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.slagalica.app.R;
import com.slagalica.app.viewmodel.AuthViewModel;

public class ChangePasswordActivity extends BaseActivity {

    private TextInputEditText etOldPassword, etNewPassword, etConfirmNewPassword;
    private MaterialButton btnChangePassword;
    private ProgressBar progressBar;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        initViews();
        setupViewModel();
        setupListeners();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        etOldPassword = findViewById(R.id.etOldPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupViewModel() {
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        authViewModel.getLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnChangePassword.setEnabled(!isLoading);
        });

        authViewModel.getPasswordChangeSuccess().observe(this, success -> {
            if (success) {
                GameToast.show(this, "Password changed successfully.", GameToast.Type.SUCCESS);
                finish();
            }
        });

        authViewModel.getErrorMessage().observe(this, error -> {
            if (error != null) GameToast.show(this, error, GameToast.Type.ERROR);
        });
    }

    private void setupListeners() {
        btnChangePassword.setOnClickListener(v -> {
            String oldPassword = etOldPassword.getText().toString();
            String newPassword = etNewPassword.getText().toString();
            String confirmNewPassword = etConfirmNewPassword.getText().toString();

            if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmNewPassword.isEmpty()) {
                GameToast.show(this, "All fields are required.", GameToast.Type.ERROR);
                return;
            }
            if (!newPassword.equals(confirmNewPassword)) {
                GameToast.show(this, "New passwords do not match.", GameToast.Type.ERROR);
                return;
            }
            if (newPassword.length() < 8) {
                GameToast.show(this, "Password must be at least 8 characters.", GameToast.Type.ERROR);
                return;
            }

            authViewModel.changePassword(oldPassword, newPassword);
        });
    }
}
