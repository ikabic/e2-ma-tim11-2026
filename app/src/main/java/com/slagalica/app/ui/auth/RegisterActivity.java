package com.slagalica.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.slagalica.app.R;
import com.slagalica.app.viewmodel.AuthViewModel;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etUsername, etPassword, etConfirmPassword;
    private MaterialAutoCompleteTextView actvRegion;
    private MaterialButton btnRegister;
    private ProgressBar progressBar;
    private AuthViewModel authViewModel;

    // change to eng?
    private final String[] regions = {
        "Beogradski region",
        "Region Vojvodine",
        "Region Šumadije i Zapadne Srbije",
        "Region Južne i Istočne Srbije"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupRegionDropdown();
        setupViewModel();
        setupListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        actvRegion = findViewById(R.id.actvRegion);
        btnRegister = findViewById(R.id.btnRegister);
        progressBar = findViewById(R.id.progressBar);

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.selectTab(tabLayout.getTabAt(1));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
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

    private void setupRegionDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_dropdown_item_1line, regions
        );
        actvRegion.setAdapter(adapter);
    }

    private void setupViewModel() {
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        authViewModel.getLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnRegister.setEnabled(!isLoading);
        });

        authViewModel.getRegistrationSuccess().observe(this, success -> {
            if (success) {
                Toast.makeText(this, "Registration successful! Check your email to verify your account.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }
        });

        authViewModel.getErrorMessage().observe(this, error -> {
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String region = actvRegion.getText().toString().trim();
            String password = etPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();

            if (!validateInput(email, username, region, password, confirmPassword)) return;

            authViewModel.register(email, username, region, password);
        });
    }

    private boolean validateInput(String email, String username, String region,
                                  String password, String confirmPassword) {
        if (email.isEmpty() || username.isEmpty() || region.isEmpty()
                || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 8 characters.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
