package com.slagalica.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.slagalica.app.R;
import com.slagalica.app.ui.auth.LoginActivity;
import com.slagalica.app.viewmodel.AuthViewModel;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        TextView tvWelcome = findViewById(R.id.tvWelcome);
        tvWelcome.setText("Welcome, " + email + "!");

        AuthViewModel authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            authViewModel.logout();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}
