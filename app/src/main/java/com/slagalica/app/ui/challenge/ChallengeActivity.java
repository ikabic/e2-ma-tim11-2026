package com.slagalica.app.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.R;
import com.slagalica.app.adapter.ChallengeListAdapter;
import com.slagalica.app.model.Challenge;
import com.slagalica.app.viewmodel.ChallengeViewModel;

public class ChallengeActivity extends AppCompatActivity {

    private ChallengeViewModel viewModel;
    private ChallengeListAdapter adapter;
    private TextView tvRegion, tvEmpty;
    private Button btnCreate;
    private String myRegion;
    private String myUsername;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge);

        viewModel = new ViewModelProvider(this).get(ChallengeViewModel.class);

        tvRegion = findViewById(R.id.tvRegion);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnCreate = findViewById(R.id.btnCreate);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvChallenges);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChallengeListAdapter(this::onChallengeTapped);
        rv.setAdapter(adapter);

        btnCreate.setEnabled(false);
        btnCreate.setOnClickListener(v -> showCreateDialog());

        viewModel.getOpenChallenges().observe(this, list -> {
            adapter.setItems(list);
            tvEmpty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getCreatedChallengeId().observe(this, id -> {
            if (id != null) {
                viewModel.clearCreatedChallengeId();
                openLobby(id);
            }
        });
        viewModel.getJoinedChallengeId().observe(this, id -> {
            if (id != null) {
                viewModel.clearJoinedChallengeId();
                openLobby(id);
            }
        });

        loadProfileThenStart();
    }

    private void loadProfileThenStart() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) { finish(); return; }
        String uid = auth.getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener(doc -> {
                myRegion = doc.getString("region");
                myUsername = doc.getString("username");
                if (myUsername == null) myUsername = "Player";
                tvRegion.setText(myRegion != null ? myRegion : "");
                btnCreate.setEnabled(myRegion != null);
                if (myRegion != null) viewModel.startListeningOpen(myRegion);
                else Toast.makeText(this, "No region set for your profile", Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e ->
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show());
    }

    private void onChallengeTapped(Challenge c) {
        String uid = viewModel.getUid();
        if (uid != null && uid.equals(c.getCreatorUid())) {
            openLobby(c.getId());
        } else {
            viewModel.joinChallenge(c.getId(), myUsername);
        }
    }

    private void showCreateDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_challenge, null);
        NumberPicker npStars = view.findViewById(R.id.npStars);
        NumberPicker npTokens = view.findViewById(R.id.npTokens);
        npStars.setMinValue(0);
        npStars.setMaxValue(10);
        npStars.setValue(5);
        npTokens.setMinValue(0);
        npTokens.setMaxValue(2);
        npTokens.setValue(1);

        new AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton("Create", (d, w) -> {
                int stars = npStars.getValue();
                int tokens = npTokens.getValue();
                if (stars == 0 && tokens == 0) {
                    Toast.makeText(this, "Stake at least 1 star or token", Toast.LENGTH_SHORT).show();
                    return;
                }
                viewModel.createChallenge(myRegion, myUsername, stars, tokens);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void openLobby(String challengeId) {
        Intent intent = new Intent(this, ChallengeLobbyActivity.class);
        intent.putExtra("challengeId", challengeId);
        intent.putExtra("username", myUsername);
        startActivity(intent);
    }
}
