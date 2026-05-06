package com.slagalica.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.R;
import com.slagalica.app.ui.auth.ChangePasswordActivity;
import com.slagalica.app.ui.game.korakpokorak.KorakPoKorakActivity;
import com.slagalica.app.ui.game.mojbroj.MojBrojActivity;
import com.slagalica.app.ui.game.asocijacije.AsocijacijeActivity;
import com.slagalica.app.ui.auth.LoginActivity;
import com.slagalica.app.ui.game.skocko.SkockoActivity;
import com.slagalica.app.viewmodel.AuthViewModel;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    String username = doc.getString("username");
                    tvWelcome.setText(username != null ? username : currentUser.getEmail());
                })
                .addOnFailureListener(e -> tvWelcome.setText(currentUser.getEmail()));
        }

        MaterialCardView cardKorakPoKorak = findViewById(R.id.cardKorakPoKorak);
        cardKorakPoKorak.setOnClickListener(v ->
            startActivity(new Intent(this, KorakPoKorakActivity.class))
        );

        MaterialCardView cardMojBroj = findViewById(R.id.cardMojBroj);
        cardMojBroj.setOnClickListener(v ->
            startActivity(new Intent(this, MojBrojActivity.class))
        );

        MaterialCardView cardAsocijacije = findViewById(R.id.cardAsocijacije);
        cardAsocijacije.setOnClickListener(v ->
                startActivity(new Intent(this, AsocijacijeActivity.class))
        );

        MaterialCardView cardSkocko = findViewById(R.id.cardSkocko);
        cardSkocko.setOnClickListener(v ->
                startActivity(new Intent(this, SkockoActivity.class))
        );

        MaterialButton btnChangePassword = findViewById(R.id.btnChangePassword);
        btnChangePassword.setOnClickListener(v ->
            startActivity(new Intent(this, ChangePasswordActivity.class))
        );

        AuthViewModel authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            authViewModel.logout();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}
