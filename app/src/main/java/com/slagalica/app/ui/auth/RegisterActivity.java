package com.slagalica.app.ui.auth;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.slagalica.app.util.GameToast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.slagalica.app.R;
import com.slagalica.app.util.ConfirmDialog;
import com.slagalica.app.viewmodel.AuthViewModel;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etUsername, etPassword, etConfirmPassword;
    private MaterialAutoCompleteTextView actvRegion;
    private MaterialButton btnRegister;
    private ProgressBar progressBar;
    private AuthViewModel authViewModel;

    private final String[] regions = {
        "Belgrade Region",
        "Vojvodina Region",
        "Šumadija and Western Serbia Region",
        "Southern and Eastern Serbia Region",
        "Kosovo and Metohija Region"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupRegionDropdown();
        setupViewModel();
        setupListeners();
        setupTermsLink();
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

    private String mapRegionToKey(String uiRegion) {
        switch (uiRegion) {
            case "Belgrade Region":
                return "beogradski_region";
            case "Vojvodina Region":
                return "vojvodina";
            case "Šumadija and Western Serbia Region":
                return "sumadija_i_zapadna_srbija";
            case "Southern and Eastern Serbia Region":
                return "juzna_i_istocna_srbija";
            case "Kosovo and Metohija Region":
                return "kosovo_i_metohija";
            default:
                return "";
        }
    }

    private void setupViewModel() {
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        authViewModel.getLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnRegister.setEnabled(!isLoading);
        });

        authViewModel.getRegistrationSuccess().observe(this, success -> {
            if (success) {
                GameToast.show(this, "Account created! Check your email to verify.", GameToast.Type.SUCCESS);
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }
        });

        authViewModel.getErrorMessage().observe(this, error -> {
            if (error != null) GameToast.show(this, error, GameToast.Type.ERROR);
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

            String regionKey = mapRegionToKey(region);
            authViewModel.register(email, username, regionKey, password);
        });
    }

    private boolean validateInput(String email, String username, String region,
                                  String password, String confirmPassword) {
        if (email.isEmpty() || username.isEmpty() || region.isEmpty()
                || password.isEmpty() || confirmPassword.isEmpty()) {
            GameToast.show(this, "All fields are required.", GameToast.Type.ERROR);
            return false;
        }
        if (!password.equals(confirmPassword)) {
            GameToast.show(this, "Passwords do not match.", GameToast.Type.ERROR);
            return false;
        }
        if (password.length() < 8) {
            GameToast.show(this, "Password must be at least 8 characters.", GameToast.Type.ERROR);
            return false;
        }
        return true;
    }

    private void setupTermsLink() {
        TextView tvTerms = findViewById(R.id.tvTerms);
        String full = "By signing in you agree to our terms.";
        SpannableString s = new SpannableString(full);
        int start = full.indexOf("terms");
        int end = start + "terms".length();
        int accentColor = getResources().getColor(R.color.accent, null);

        s.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                ConfirmDialog.showInfo(RegisterActivity.this,
                    "Fair Play",
                    "Play by the rules and keep it fun for everyone.\n\n" +
                    "No cheating, no unsportsmanlike behavior.\n" +
                    "Respect your opponents and enjoy the game!",
                    "Got it!");
            }
            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(accentColor);
                ds.setUnderlineText(true);
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(accentColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvTerms.setText(s);
        tvTerms.setMovementMethod(LinkMovementMethod.getInstance());
        tvTerms.setHighlightColor(Color.TRANSPARENT);
    }
}
