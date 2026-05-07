package com.slagalica.app.ui.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.slagalica.app.R;
import com.slagalica.app.databinding.ActivityProfileBinding;
import com.slagalica.app.ui.auth.LoginActivity;
import com.slagalica.app.util.ConfirmDialog;
import com.slagalica.app.util.QRCodeGenerator;
import com.slagalica.app.viewmodel.AuthViewModel;
import com.slagalica.app.viewmodel.ProfileViewModel;

public class ProfileActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> pickImageLauncher;
    private ProfileViewModel viewModel;
    private ActivityProfileBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerPickerLauncher();

        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupViewModel();
        viewModel.loadProfile();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Bitmap qrBitmap = QRCodeGenerator.generateQRCode(user.getUid());
        binding.ivQrCode.setImageBitmap(qrBitmap);

        binding.btnEditAvatar.setOnClickListener(v ->
                pickImageLauncher.launch("image/*")
        );

        AuthViewModel authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        binding.btnLogout.setOnClickListener(v ->
                ConfirmDialog.show(this, "Log out?", "You'll need to sign in again.",
                        "Log out", "Cancel", () -> {
                            authViewModel.logout();
                            startActivity(new Intent(this, LoginActivity.class));
                            finish();
                        })
        );
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        viewModel.getUser().observe(this, user -> {
            binding.tvUsername.setText(user.getUsername());
            binding.tvEmail.setText(user.getEmail());
            binding.tvRegion.setText(user.getRegion());
        });

        viewModel.getProfile().observe(this, profile -> {
            int stars = profile.getStars();
            int points = Integer.parseInt(profile.getLeague("points"));
            int progress = (stars * 100) / points;

            setLeague(profile.getLeague("name"));

            binding.tvTokens.setText(profile.getTokens() + " tokens");
            binding.tvStars.setText(stars + " / " + points + " stars");
            binding.lpiLeagueProgress.setProgress(progress);

            int needed = Math.max(points - stars, 0);
            binding.tvNextLeague.setText(needed + " stars until " + profile.getLeague("next") + " League");

            if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                Glide.with(this)
                        .load(profile.getAvatarUrl())
                        .circleCrop()
                        .into(binding.ivAvatar);
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        });
    }

    private void registerPickerLauncher() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    Glide.with(this).load(uri).circleCrop().into(binding.ivAvatar);
                }
        );
    }

    private void setLeague(String league) {

        binding.tvLeague.setText(league + " League");

        switch (league) {

            case "Bronze":
                binding.tvLeague.setTextColor(getColor(R.color.league_bronze));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_bronze);
                break;

            case "Silver":
                binding.tvLeague.setTextColor(getColor(R.color.league_silver));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_silver);
                break;

            case "Gold":
                binding.tvLeague.setTextColor(getColor(R.color.league_gold));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_gold);
                break;

            case "Platinum":
                binding.tvLeague.setTextColor(getColor(R.color.league_platinum));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_platinum);
                break;

            case "Diamond":
                binding.tvLeague.setTextColor(getColor(R.color.league_diamond));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_diamond);
                break;

            default:
                binding.tvLeague.setText(league);
                binding.tvLeague.setTextColor(getColor(R.color.league_unranked));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_unranked);
                break;
        }
    }
}