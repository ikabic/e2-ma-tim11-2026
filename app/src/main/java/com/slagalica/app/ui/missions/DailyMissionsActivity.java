package com.slagalica.app.ui.missions;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;
import com.slagalica.app.R;
import com.slagalica.app.databinding.ActivityDailyMissionsBinding;
import com.slagalica.app.model.DailyMissions;
import com.slagalica.app.repository.DailyMissionRepository;
import com.slagalica.app.repository.RepositoryCallback;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DailyMissionsActivity extends AppCompatActivity {

    private ActivityDailyMissionsBinding binding;
    private DailyMissionRepository repo;
    private ListenerRegistration listenerReg;
    private DailyMissions currentMissions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDailyMissionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repo = new DailyMissionRepository();

        String dateStr = new SimpleDateFormat("d. MMMM yyyy.", Locale.getDefault()).format(new Date());
        binding.tvDateLabel.setText(dateStr);

        setupMissionRow(binding.rowWinMatch.getRoot(), R.drawable.ic_award,"Win a match", "Win 1 match");
        setupMissionRow(binding.rowSendChat.getRoot(), R.drawable.ic_chat, "Send chat message", "Send message in regional chat");
        setupMissionRow(binding.rowPlayFriendly.getRoot(), R.drawable.ic_friends,"Play a friendly match","Invite friend for a friendly match");
        setupMissionRow(binding.rowWinTournament.getRoot(), R.drawable.ic_trophy, "Win a tournament", "Win a tournament match");

        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnClaim.setOnClickListener(v -> claimRewards());

        listenerReg = repo.listenTodayMissions(missions -> {
            currentMissions = missions;
            updateUI(missions);
        });

        setLoading(true);
        repo.loadTodayMissions(new RepositoryCallback<DailyMissions>() {
            @Override public void onSuccess(DailyMissions m) {
                setLoading(false);
                currentMissions = m;
                updateUI(m);
            }
            @Override public void onFailure(Exception e) {
                setLoading(false);
                Toast.makeText(DailyMissionsActivity.this, "Error while loading missions", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupMissionRow(View row, @DrawableRes int iconRes, String title, String subtitle) {
        ImageView ivIcon = row.findViewById(R.id.tvMissionIcon);
        TextView tvTitle = row.findViewById(R.id.tvMissionTitle);
        TextView tvReward = row.findViewById(R.id.tvMissionReward);

        if (ivIcon != null) ivIcon.setImageResource(iconRes);
        if (tvTitle != null) tvTitle.setText(title);
        if (tvReward != null) tvReward.setText(subtitle + "  ·  +3 ⭐");
    }

    private void updateUI(DailyMissions m) {
        setMissionRowState(binding.rowWinMatch.getRoot(), m.isWinMatch());
        setMissionRowState(binding.rowSendChat.getRoot(), m.isSendChat());
        setMissionRowState(binding.rowPlayFriendly.getRoot(), m.isPlayFriendly());
        setMissionRowState(binding.rowWinTournament.getRoot(), m.isWinTournament());

        int done = m.completedCount();
        binding.tvProgress.setText(done + " / 4 missions completed");
        binding.progressMissions.setMax(4);
        binding.progressMissions.setProgress(done);

        if (m.allCompleted()) {
            binding.tvBonusHint.setText("All missions completed! Bonus: +2 tokens and +3 ⭐");
        } else {
            int remaining = 4 - done;
            binding.tvBonusHint.setText("Complete " + remaining + " more for bonus: +2 tokens i +3 ⭐");
        }

        boolean canClaim = m.hasUnclaimedRewards();

        binding.btnClaim.setEnabled(canClaim);
        binding.btnClaim.setAlpha(canClaim ? 1f : 0.45f);

        if (done == 0) {
            binding.btnClaim.setText("Complete mission for rewards");
        } else if (!canClaim) {
            binding.btnClaim.setText("Rewards claimed");
        } else {
            int stars = m.totalStarsAvailable();
            int tokens = (m.allCompleted() && m.getClaimedCount() < 4) ? 2 : 0;
            String tokenStr = tokens > 0 ? " + " + tokens + " tokens" : "";
            binding.btnClaim.setText("Claim +" + stars + " ⭐" + tokenStr);
        }
    }

    private void setMissionRowState(View row, boolean completed) {
        View iconDone = row.findViewById(R.id.ivMissionDone);
        View iconTodo = row.findViewById(R.id.ivMissionTodo);
        if (iconDone != null) iconDone.setVisibility(completed ? View.VISIBLE : View.GONE);
        if (iconTodo != null) iconTodo.setVisibility(completed ? View.GONE   : View.VISIBLE);
        row.setAlpha(completed ? 0.55f : 1f);
    }

    private void setLoading(boolean loading) {
        binding.scroll.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        binding.btnClaim.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
    }

    private void claimRewards() {
        if (currentMissions == null) return;
        if (!currentMissions.hasUnclaimedRewards()) return;
        binding.btnClaim.setEnabled(false);

        repo.claimRewards(currentMissions,
                new RepositoryCallback<DailyMissionRepository.ClaimResult>() {
                    @Override
                    public void onSuccess(DailyMissionRepository.ClaimResult result) {
                        String msg;
                        if (result.bonusUnlocked) {
                            msg = "+" + result.starsEarned + " ⭐ i +" + result.tokensEarned + " tokens claimed!";
                        } else {
                            msg = "+" + result.starsEarned + " ⭐ claimed!";
                        }
                        Toast.makeText(DailyMissionsActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onFailure(Exception e) {
                        Toast.makeText(DailyMissionsActivity.this,
                                "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        if (currentMissions != null && currentMissions.hasUnclaimedRewards())
                            binding.btnClaim.setEnabled(true);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerReg != null) listenerReg.remove();
    }
}
