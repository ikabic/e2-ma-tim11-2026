package com.slagalica.app.ui.profile;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.slagalica.app.R;
import com.slagalica.app.databinding.FragmentProfileBinding;
import com.slagalica.app.util.ProfileUtils;
import com.slagalica.app.util.QRCodeGenerator;
import com.slagalica.app.viewmodel.ProfileViewModel;

public class ProfileFragment extends Fragment {

    private ActivityResultLauncher<String> pickImageLauncher;
    private ProfileViewModel viewModel;
    private FragmentProfileBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerPickerLauncher();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            Toast.makeText(requireContext(), "Register to access your profile", Toast.LENGTH_SHORT).show();
            return;
        }

        setupViewModel();
        viewModel.loadProfile();

        Bitmap qrBitmap = QRCodeGenerator.generateQRCode(user.getUid());
        binding.ivQrCode.setImageBitmap(qrBitmap);

        binding.btnEditAvatar.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(requireActivity()).get(ProfileViewModel.class);

        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            binding.tvUsername.setText(user.getUsername());
            binding.tvEmail.setText(user.getEmail());
            binding.tvRegion.setText(user.getRegion());
        });

        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> {
            int stars = profile.getStars();
            int points = Integer.parseInt(profile.getLeague("points"));
            int progress = (stars * 100) / points;

            setLeague(profile.getLeague("name"));

            binding.tvStars.setText(stars + " / " + points + " stars");
            binding.lpiLeagueProgress.setProgress(progress);

            int needed = Math.max(points - stars, 0);
            binding.tvNextLeague.setText(needed + " stars until " + profile.getLeague("next") + " League");

            if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                Glide.with(this).load(profile.getAvatarUrl()).circleCrop().into(binding.ivAvatar);
            } else {
                binding.ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
            }

            ProfileUtils.applyRegionFrame(binding.ivRegionAwardFrame, profile.getPrevCycleRegionRank(), binding.ivAvatar);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
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
                binding.tvLeague.setTextColor(requireContext().getColor(R.color.league_bronze));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_bronze);
                break;
            case "Silver":
                binding.tvLeague.setTextColor(requireContext().getColor(R.color.league_silver));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_silver);
                break;
            case "Gold":
                binding.tvLeague.setTextColor(requireContext().getColor(R.color.league_gold));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_gold);
                break;
            case "Platinum":
                binding.tvLeague.setTextColor(requireContext().getColor(R.color.league_platinum));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_platinum);
                break;
            case "Diamond":
                binding.tvLeague.setTextColor(requireContext().getColor(R.color.league_diamond));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_diamond);
                break;
            default:
                binding.tvLeague.setText(league);
                binding.tvLeague.setTextColor(requireContext().getColor(R.color.league_unranked));
                binding.ivLeagueBadge.setImageResource(R.drawable.league_unranked);
                break;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}