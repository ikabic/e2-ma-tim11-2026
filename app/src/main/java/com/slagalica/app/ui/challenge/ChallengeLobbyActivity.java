package com.slagalica.app.ui.challenge;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.slagalica.app.R;
import com.slagalica.app.adapter.ChallengeParticipantAdapter;
import com.slagalica.app.model.Challenge;
import com.slagalica.app.repository.ChallengeRepository.ChallengeState;
import com.slagalica.app.repository.ChallengeRepository.Participant;
import com.slagalica.app.viewmodel.ChallengeViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChallengeLobbyActivity extends AppCompatActivity {

    private ChallengeViewModel viewModel;
    private ChallengeParticipantAdapter adapter;
    private String challengeId, myUsername, myUid;

    private TextView tvStakeStars, tvStakeTokens, tvStatus, tvWaiting, tvResultBanner;
    private Button btnAction;
    private boolean resolveTriggered = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_lobby);

        challengeId = getIntent().getStringExtra("challengeId");
        myUsername = getIntent().getStringExtra("username");

        viewModel = new ViewModelProvider(this).get(ChallengeViewModel.class);
        myUid = viewModel.getUid();

        tvStakeStars = findViewById(R.id.tvStakeStars);
        tvStakeTokens = findViewById(R.id.tvStakeTokens);
        tvStatus = findViewById(R.id.tvStatus);
        tvWaiting = findViewById(R.id.tvWaiting);
        tvResultBanner = findViewById(R.id.tvResultBanner);
        btnAction = findViewById(R.id.btnAction);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvParticipants);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChallengeParticipantAdapter();
        rv.setAdapter(adapter);

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getCurrentChallenge().observe(this, this::render);

        if (challengeId != null) viewModel.startListeningChallenge(challengeId);
    }

    private void render(ChallengeState state) {
        if (state == null || state.challenge == null) return;
        Challenge ch = state.challenge;
        List<Participant> ps = state.participants != null ? state.participants : new ArrayList<>();

        tvStakeStars.setText(String.valueOf(ch.getStarsStake()));
        tvStakeTokens.setText(String.valueOf(ch.getTokensStake()));

        String status = ch.getStatus();
        boolean isCreator = myUid != null && myUid.equals(ch.getCreatorUid());

        if ("playing".equals(status)) {
            renderPlaying(ch, ps);
        } else if ("finished".equals(status)) {
            renderFinished(ch, ps, state.winnerUid, state.secondUid);
        } else {
            renderOpen(ch, ps, isCreator);
        }
    }

    private void renderOpen(Challenge ch, List<Participant> ps, boolean isCreator) {
        tvResultBanner.setVisibility(View.GONE);
        adapter.setData(ps, "open", null, null);
        tvStatus.setText("Waiting for players · " + ps.size() + "/4");

        if (isCreator) {
            tvWaiting.setVisibility(View.GONE);
            btnAction.setVisibility(View.VISIBLE);
            btnAction.setText("Start challenge");
            btnAction.setEnabled(ps.size() >= 2);
            btnAction.setOnClickListener(v -> viewModel.startChallenge(challengeId));
        } else {
            btnAction.setVisibility(View.GONE);
            tvWaiting.setVisibility(View.VISIBLE);
            tvWaiting.setText("Waiting for the host to start…");
        }
    }

    private void renderPlaying(Challenge ch, List<Participant> ps) {
        tvResultBanner.setVisibility(View.GONE);
        adapter.setData(ps, "playing", null, null);

        int played = 0;
        Participant me = null;
        for (Participant p : ps) {
            if (p.played) played++;
            if (myUid != null && myUid.equals(p.uid)) me = p;
        }
        tvStatus.setText("In progress · " + played + "/" + ps.size() + " played");

        boolean iPlayed = me != null && me.played;
        if (!iPlayed) {
            tvWaiting.setVisibility(View.GONE);
            btnAction.setVisibility(View.VISIBLE);
            btnAction.setText("Play solo partija");
            btnAction.setEnabled(true);
            btnAction.setOnClickListener(v -> startPartija());
        } else {
            btnAction.setVisibility(View.GONE);
            tvWaiting.setVisibility(View.VISIBLE);
            tvWaiting.setText("Waiting for other players to finish…");
        }

        if (played == ps.size() && !ps.isEmpty() && !resolveTriggered) {
            resolveTriggered = true;
            viewModel.resolveIfReady(challengeId);
        }
    }

    private void renderFinished(Challenge ch, List<Participant> ps, String winnerUid, String secondUid) {
        btnAction.setVisibility(View.GONE);
        tvWaiting.setVisibility(View.GONE);
        tvStatus.setText("Challenge finished");

        List<Participant> sorted = new ArrayList<>(ps);
        Collections.sort(sorted, (a, b) -> b.score - a.score);
        adapter.setData(sorted, "finished", winnerUid, secondUid);

        int n = ps.size();
        int wStars = (int) (n * ch.getStarsStake() * 0.75);
        int wTokens = (int) (n * ch.getTokensStake() * 0.75);

        String txt;
        if (myUid != null && myUid.equals(winnerUid)) {
            txt = "You won!  +" + wStars + " stars  +" + wTokens + " tokens";
        } else if (myUid != null && myUid.equals(secondUid)) {
            txt = "2nd place — your stake was returned";
        } else {
            txt = "Winner: " + nameOf(ps, winnerUid);
        }
        tvResultBanner.setText(txt);
        tvResultBanner.setVisibility(View.VISIBLE);
    }

    private String nameOf(List<Participant> ps, String uid) {
        if (uid == null) return "—";
        for (Participant p : ps) {
            if (uid.equals(p.uid)) return p.username;
        }
        return "—";
    }

    private void startPartija() {
        Intent intent = new Intent(this, ChallengePartijaActivity.class);
        intent.putExtra("challengeId", challengeId);
        intent.putExtra("username", myUsername);
        startActivity(intent);
    }
}
