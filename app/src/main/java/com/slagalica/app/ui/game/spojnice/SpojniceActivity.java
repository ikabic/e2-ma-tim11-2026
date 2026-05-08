package com.slagalica.app.ui.game.spojnice;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.slagalica.app.databinding.ActivitySpojniceBinding;
import com.slagalica.app.model.SpojniceQuestion;
import com.slagalica.app.util.ConfirmDialog;
import com.slagalica.app.viewmodel.SpojniceViewModel;

import java.util.List;

public class SpojniceActivity extends AppCompatActivity {

    private SpojniceViewModel viewModel;
    private ActivitySpojniceBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySpojniceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupViewModel();

        binding.header.tvGameTitle.setText("Connections");
        binding.header.btnClose.setOnClickListener(v -> showExitConfirm());
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(SpojniceViewModel.class);

        viewModel.getCurrentIndex().observe(this, index -> {
            int round = index + 1;
            binding.header.tvRound.setText("Round " + round + "/2");

            renderQuestion();
            viewModel.startQuestion();
        });

        viewModel.getTimeLeft().observe(this, timeLeft ->
                binding.header.tvTimer.setText(String.valueOf((long) Math.ceil(timeLeft / 1000.0) + "s"))
        );

        viewModel.getP1Score().observe(this, s -> binding.header.tvScore.setText(String.valueOf(s)));
        viewModel.getP2Score().observe(this, s -> binding.header.tvScoreOpponent.setText(String.valueOf(s)));
    }

    private void renderQuestion() {
        List<SpojniceQuestion> questions = viewModel.getQuestions().getValue();
        Integer index = viewModel.getCurrentIndex().getValue();

        if (questions == null || index == null || questions.isEmpty()) return;

        SpojniceQuestion question = questions.get(index);

        binding.tvQuestion.setText(question.getText());

        binding.tvLeft1.setText(question.getLeftTerms().get(0));
        binding.tvLeft2.setText(question.getLeftTerms().get(1));
        binding.tvLeft3.setText(question.getLeftTerms().get(2));
        binding.tvLeft4.setText(question.getLeftTerms().get(3));
        binding.tvLeft5.setText(question.getLeftTerms().get(4));

        binding.tvRight1.setText(question.getRightTerms().get(0));
        binding.tvRight2.setText(question.getRightTerms().get(1));
        binding.tvRight3.setText(question.getRightTerms().get(2));
        binding.tvRight4.setText(question.getRightTerms().get(3));
        binding.tvRight5.setText(question.getRightTerms().get(4));

        viewModel.startQuestion();
    }

    private void showExitConfirm() {
        ConfirmDialog.show(this, "Quit game?", "Your progress will be lost.",
                "Quit", "Keep playing", this::finish);
    }
}
