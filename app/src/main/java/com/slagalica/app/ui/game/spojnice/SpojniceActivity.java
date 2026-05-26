package com.slagalica.app.ui.game.spojnice;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.slagalica.app.R;
import com.slagalica.app.databinding.ActivitySpojniceBinding;
import com.slagalica.app.model.SpojniceQuestion;
import com.slagalica.app.util.ConfirmDialog;
import com.slagalica.app.viewmodel.SpojniceViewModel;

import java.util.List;
import java.util.Map;

public class SpojniceActivity extends AppCompatActivity {

    private static final long WRONG_FLASH_MS = 500L;

    private SpojniceViewModel viewModel;
    private ActivitySpojniceBinding binding;

    private boolean isMatchGame;
    private boolean isPlayer1;
    private boolean gameFinished = false;
    private boolean isRevealScreen = false;
    private String opponent = "Opponent";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySpojniceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        isMatchGame = getIntent().getBooleanExtra("isMatchGame", false);
        isPlayer1 = getIntent().getBooleanExtra("isPlayer1", true);
        String matchId = getIntent().getStringExtra("matchId");
        String opponentUsername = getIntent().getStringExtra("opponentUsername");
        opponent = opponentUsername == null ? "Opponent" : opponentUsername;

        viewModel = new ViewModelProvider(this).get(SpojniceViewModel.class);

        if (isMatchGame && matchId != null)
            viewModel.initMatchMode(matchId, isPlayer1);
        else
            viewModel.loadQuestionsForSolo();

        binding.header.tvGameTitle.setText("Connections");
        binding.header.btnClose.setOnClickListener(v -> showExitConfirm());
        binding.header.tvOpponentName.setText(opponent);

        setupRightCardListeners();
        setupObservers();
    }

    private void setupRightCardListeners() {
        MaterialCardView[] rights = rightCards();
        for (int i = 0; i < rights.length; i++) {
            final int idx = i;
            rights[i].setOnClickListener(v -> viewModel.tapRightTerm(idx));
        }
    }

    private void setupObservers() {
        viewModel.getQuestions().observe(this, questions -> {
            if (questions == null) return;
            Integer round = viewModel.getCurrentRound().getValue();
            renderRound(round != null ? round : 0, questions);
        });

        viewModel.getCurrentRound().observe(this, round -> {
            if (round == null) return;
            List<SpojniceQuestion> qs = viewModel.getQuestions().getValue();
            if (qs != null) renderRound(round, qs);
            binding.header.tvRound.setText("Round " + (round + 1) + " / 2");
        });

        viewModel.getMyActiveTurn().observe(this, active -> {
            if (active == null) return;
            String label = active ? "Your turn" : opponent + "'s turn";
            binding.tvActivePlayer.setText(label);
        });

        viewModel.getWaitingForOpp().observe(this, waiting -> {
            if (waiting == null) return;
            binding.waitingOverlay.setVisibility(waiting ? View.VISIBLE : View.GONE);
        });

        viewModel.getTimeLeft().observe(this, ms -> {
            if (ms == null) return;
            binding.header.tvTimer.setText((long) Math.ceil(ms / 1000.0) + "s");
        });

        viewModel.getMyRunningScore().observe(this, score -> {
            if (score == null) return;
            binding.header.tvScore.setText(String.valueOf(score));
        });

        viewModel.getOpponentRunningScore().observe(this, score -> {
            if (score == null) return;
            try { binding.header.tvScoreOpponent.setText(String.valueOf(score)); }
            catch (NullPointerException ignored) {}
        });

        viewModel.getOpponentAnswerResult().observe(this, result -> {
            if (result == null) return;
            int rightIdx = result[0];
            boolean correct = result[1] == 1;

            if (!correct) flashWrong(rightIdx);
        });

        viewModel.getOpponentActiveLeftIdx().observe(this, idx -> {
            if (idx == null || idx == -1 || isRevealScreen) return;

            Boolean myTurn = viewModel.getMyActiveTurn().getValue();
            if (myTurn != null && !myTurn) highlightActiveLeft(idx);
        });

        viewModel.getActiveLeftIdx().observe(this, idx -> {
            if (idx == null || isRevealScreen) return;
            highlightActiveLeft(idx);
        });

        viewModel.getPairOwners().observe(this, owners -> {
            Map<Integer, Integer> pairs = viewModel.getConfirmedPairs().getValue();
            if (pairs != null) applyConfirmedPairs(pairs);
        });

        viewModel.getConfirmedPairs().observe(this, pairs -> {
            if (pairs == null) return;
            applyConfirmedPairs(pairs);
        });

        viewModel.getAnswerResult().observe(this, result -> {
            if (result == null) return;
            int rightIdx = result[0];
            boolean correct = result[1] == 1;
            if (!correct) flashWrong(rightIdx);
        });

        viewModel.getOpponentLeft().observe(this, left -> {
            if (Boolean.TRUE.equals(left)) {
                binding.tvActivePlayer.setText(opponent + " disconnected");
            }
        });

        viewModel.getRevealedCorrectPairs().observe(this, correctPairs -> {
            if (correctPairs == null) return;
            revealCorrectLayout(correctPairs);
        });

        viewModel.getFinalScores().observe(this, scores -> {
            if (scores == null || gameFinished) return;
            gameFinished = true;
            if (isMatchGame) {
                Intent result = new Intent();
                result.putExtra("p1Score", scores[0]);
                result.putExtra("p2Score", scores[1]);
                setResult(RESULT_OK, result);
            }
            finish();
        });
    }

    private void renderRound(int round, List<SpojniceQuestion> questions) {
        isRevealScreen = false;
        if (round >= questions.size()) return;
        SpojniceQuestion q = questions.get(round);
        binding.tvQuestion.setText(q.getText());

        MaterialCardView[] lefts = leftCards();
        MaterialCardView[] rights = rightCards();

        List<String> leftTerms = q.getLeftTerms();
        List<String> rightTerms = q.getRightTerms();

        for (int i = 0; i < 5; i++) {
            setCardText(lefts[i], leftTerms.get(i));
            setCardText(rights[i], rightTerms.get(i));
            resetCardStyle(lefts[i]);
            resetCardStyle(rights[i]);
        }
    }

    private void highlightActiveLeft(int idx) {
        MaterialCardView[] lefts = leftCards();
        for (int i = 0; i < lefts.length; i++) {
            if (i == idx)
                applyCardStyle(lefts[i], getColor(R.color.accent), false);
            else if (!isPaired(i))
                resetCardStyle(lefts[i]);
        }
    }

    private void applyConfirmedPairs(Map<Integer, Integer> pairs) {
        MaterialCardView[] lefts = leftCards();
        MaterialCardView[] rights = rightCards();

        if (pairs == null || pairs.isEmpty()) {
            for (int i = 0; i < 5; i++) {
                resetCardStyle(lefts[i]);
                resetCardStyle(rights[i]);
            }
            return;
        }

        Map<Integer, Boolean> owners = viewModel.getPairOwners().getValue();

        for (Map.Entry<Integer, Integer> e : pairs.entrySet()) {
            int l = e.getKey();
            int r = e.getValue();

            if (owners != null && owners.containsKey(l)) {
                boolean isP1Owner = owners.get(l);
                int strokeColor = (isP1Owner == isPlayer1) ? getColor(R.color.accent) : getColor(R.color.black);

                if (l < lefts.length) applyCardStyle(lefts[l], strokeColor, true);
                if (r < rights.length) applyCardStyle(rights[r], strokeColor, true);
            } else {
                if (l < lefts.length) resetCardStyle(lefts[l]);
                if (r < rights.length) resetCardStyle(rights[r]);
            }
        }
    }

    private void flashWrong(int rightIdx) {
        MaterialCardView[] rights = rightCards();
        if (rightIdx < 0 || rightIdx >= rights.length) return;
        MaterialCardView card = rights[rightIdx];
        card.setStrokeColor(getColor(R.color.danger));
        card.setStrokeWidth(dpToPx(2));
        card.postDelayed(() -> {
            Map<Integer, Integer> pairs = viewModel.getConfirmedPairs().getValue();
            boolean alreadyPaired = pairs != null && pairs.containsValue(rightIdx);
            if (!alreadyPaired) resetCardStyle(card);
        }, WRONG_FLASH_MS);
    }

    private void revealCorrectLayout(Map<Integer, Integer> correctPairs) {
        isRevealScreen = true;

        Integer round = viewModel.getCurrentRound().getValue();
        List<SpojniceQuestion> qs = viewModel.getQuestions().getValue();
        if (round == null || qs == null || round >= qs.size()) return;

        SpojniceQuestion q = qs.get(round);

        List<String> leftTerms = q.getLeftTerms();
        List<String> rightTerms = q.getRightTerms();

        MaterialCardView[] lefts = leftCards();
        MaterialCardView[] rights = rightCards();

        Map<Integer, Boolean> owners = viewModel.getPairOwners().getValue();

        for (int i = 0; i < 5; i++) {
            setCardText(lefts[i], leftTerms.get(i));

            Integer correctRightIdx = correctPairs.get(i);

            if (correctRightIdx != null) {
                setCardText(rights[i], rightTerms.get(correctRightIdx));

                if (owners != null && owners.containsKey(i)) {
                    boolean isP1Owner = owners.get(i);
                    int strokeColor = (isP1Owner == isPlayer1) ? getColor(R.color.accent) : getColor(R.color.black);

                    applyCardStyle(lefts[i], strokeColor, true);
                    applyCardStyle(rights[i], strokeColor, true);
                } else {
                    resetCardStyle(lefts[i]);
                    resetCardStyle(rights[i]);
                }
            }
        }
    }

    private boolean isPaired(int leftIdx) {
        Map<Integer, Integer> pairs = viewModel.getConfirmedPairs().getValue();
        return pairs != null && pairs.containsKey(leftIdx);
    }

    private void setCardText(MaterialCardView card, String text) {
        if (card.getChildCount() > 0 && card.getChildAt(0) instanceof TextView) {
            ((TextView) card.getChildAt(0)).setText(text);
        }
    }

    private void applyCardStyle(MaterialCardView card, int strokeColor, boolean colorBackground) {
        card.setStrokeColor(strokeColor);
        card.setStrokeWidth(dpToPx(2));

        if (colorBackground) card.setCardBackgroundColor(strokeColor);
        else card.setCardBackgroundColor(getColor(R.color.bg_card));
    }

    private void resetCardStyle(MaterialCardView card) {
        card.setStrokeColor(getColor(R.color.border));
        card.setCardBackgroundColor(getColor(R.color.bg_card));
        card.setStrokeWidth(dpToPx(1));
    }

    private MaterialCardView[] leftCards() {
        return new MaterialCardView[]{ binding.leftItem1, binding.leftItem2, binding.leftItem3, binding.leftItem4, binding.leftItem5 };
    }

    private MaterialCardView[] rightCards() {
        return new MaterialCardView[]{ binding.rightItem1, binding.rightItem2, binding.rightItem3, binding.rightItem4, binding.rightItem5 };
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showExitConfirm() {
        ConfirmDialog.show(this, "Quit game?", "Your progress will be lost.", "Quit", "Keep playing",
                () -> {
                    viewModel.writeForfeit();
                    finish();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!gameFinished) viewModel.writeForfeit();
    }
}