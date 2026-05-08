package com.slagalica.app.ui.game.koznazna;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.slagalica.app.databinding.ActivityKoZnaZnaBinding;
import com.slagalica.app.model.KoZnaZnaQuestion;
import com.slagalica.app.util.ConfirmDialog;
import com.slagalica.app.viewmodel.KoZnaZnaViewModel;

import java.util.List;

public class KoZnaZnaActivity extends AppCompatActivity {

    private KoZnaZnaViewModel viewModel;
    private ActivityKoZnaZnaBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityKoZnaZnaBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupViewModel();

        binding.header.tvGameTitle.setText("Who knows, knows");
        binding.header.btnClose.setOnClickListener(v -> showExitConfirm());
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(KoZnaZnaViewModel.class);

        viewModel.getCurrentIndex().observe(this, index -> {
            int question = index + 1;
            binding.header.tvRound.setText("Question " + question + "/5");

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
        List<KoZnaZnaQuestion> questions = viewModel.getQuestions().getValue();
        Integer index = viewModel.getCurrentIndex().getValue();

        if (questions == null || index == null || questions.isEmpty()) return;

        KoZnaZnaQuestion question = questions.get(index);

        binding.tvQuestion.setText(question.getText());

        binding.btnAnswerA.setText(question.getAnswers().get(0));
        binding.btnAnswerB.setText(question.getAnswers().get(1));
        binding.btnAnswerC.setText(question.getAnswers().get(2));
        binding.btnAnswerD.setText(question.getAnswers().get(3));

        viewModel.startQuestion();
    }

    private void showExitConfirm() {
        ConfirmDialog.show(this, "Quit game?", "Your progress will be lost.",
                "Quit", "Keep playing", this::finish);
    }
}
