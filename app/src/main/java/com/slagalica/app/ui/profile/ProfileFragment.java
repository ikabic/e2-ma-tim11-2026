package com.slagalica.app.ui.profile;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.slagalica.app.R;
import com.slagalica.app.databinding.FragmentProfileBinding;
import com.slagalica.app.model.GameStatistics;
import com.slagalica.app.repository.RegionRepository;
import com.slagalica.app.util.ProfileUtils;
import com.slagalica.app.util.QRCodeGenerator;
import com.slagalica.app.viewmodel.ProfileViewModel;

import java.util.Map;

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

        String targetUid = getArguments() != null ? getArguments().getString("USER_UID") : null;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uidToLoad = (targetUid != null) ? targetUid : (currentUser != null ? currentUser.getUid() : null);

        if (uidToLoad == null || (currentUser != null && currentUser.isAnonymous() && targetUid == null)) {
            Toast.makeText(requireContext(), "Register to access your profile", Toast.LENGTH_SHORT).show();
            return;
        }

        setupViewModel();
        viewModel.loadProfile(uidToLoad);

        viewModel.loadPlayerStats(uidToLoad);
        setupStatisticsObserver();

        Bitmap qrBitmap = QRCodeGenerator.generateQRCode(uidToLoad);
        binding.ivQrCode.setImageBitmap(qrBitmap);

        Boolean isMe = (targetUid != null && currentUser != null && targetUid.equals(currentUser.getUid()));

        if (targetUid != null && !isMe) {
            binding.btnEditAvatar.setVisibility(View.GONE);
            binding.personalSection.setVisibility(View.GONE);
        }
        else {
            binding.btnEditAvatar.setVisibility(View.VISIBLE);
            binding.personalSection.setVisibility(View.VISIBLE);
            binding.btnEditAvatar.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        }
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading != null) {
                if (isLoading) {
                    binding.pbLoading.setVisibility(View.VISIBLE);
                    binding.profile.setVisibility(View.GONE);
                } else {
                    binding.pbLoading.setVisibility(View.GONE);
                    binding.profile.setVisibility(View.VISIBLE);
                }
            }
        });

        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user == null) return;
            binding.tvUsername.setText(user.getUsername());
            binding.tvEmail.setText(user.getEmail());

            RegionRepository regionRepo = new RegionRepository();
            String region = regionRepo.keyToDisplayName(user.getRegion());
            binding.tvRegion.setText(region);
        });

        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile == null) return;
            int stars = profile.getStars();
            int points = Integer.parseInt(profile.getLeague("points"));

            setLeague(profile.getLeague("name"));

            binding.tvStars.setText(stars + " / " + points + " stars");
            binding.lpiLeagueProgress.setMin(points == 100 ? 0 : points / 2);
            binding.lpiLeagueProgress.setMax(points);
            binding.lpiLeagueProgress.setProgress(stars, true);

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

    private void setupStatisticsObserver() {
        viewModel.getPlayerStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats == null) return;

            int totalPlayed = stats.getMatchesPlayed();
            int wins = stats.getMatchOutcomes().first;
            int losses = stats.getMatchOutcomes().second;
            int totalDecided = wins + losses;

            binding.tvTotalGames.setText(String.valueOf(totalPlayed));
            binding.tvWinCount.setText(String.valueOf(wins));
            binding.tvLossCount.setText(String.valueOf(losses));

            LinearLayout.LayoutParams winParams = (LinearLayout.LayoutParams) binding.vTotalWins.getLayoutParams();
            LinearLayout.LayoutParams lossParams = (LinearLayout.LayoutParams) binding.vTotalLosses.getLayoutParams();

            if (totalDecided > 0) {
                int winPct = (wins * 100) / totalDecided;
                int lossPct = (losses * 100) / totalDecided;
                binding.tvWinPct.setText(winPct + "%");
                binding.tvLossPct.setText(lossPct + "%");

                winParams.weight = winPct;
                lossParams.weight = lossPct;
            } else {
                binding.tvWinPct.setText("0%");
                binding.tvLossPct.setText("0%");

                winParams.weight = lossParams.weight = 50;
            }

            binding.vTotalWins.setLayoutParams(winParams);
            binding.vTotalLosses.setLayoutParams(lossParams);

            int totalPoints = stats.getGames()
                    .stream()
                    .mapToInt(GameStatistics::getTotalPoints)
                    .sum();

            int avgPoints = totalPlayed > 0 ? totalPoints / totalPlayed : 0;
            binding.tvAvgPoints.setText(String.valueOf(avgPoints));

            for (GameStatistics gs : stats.getGames()) {
                int gameId = gs.getGameId();
                int played = gs.getTotalPlayed();
                int points = gs.getTotalPoints();

                switch (gameId) {
                    case 0: // Ko Zna Zna
                        updateRangeBar(binding.tvMinKzz, binding.tvMaxKzz, binding.rangeBarKzzLeft, binding.rangeBarKzzFill, binding.rangeBarKzzRight, binding.tvRangeKzz,
                                gs.getMinPoints(), gs.getMaxPoints(), points, played);

                        long correctKzz = gs.getMetric("correctGuesses");
                        long wrongKzz = gs.getMetric("wrongGuesses");
                        long totalKzz = correctKzz + wrongKzz;

                        LinearLayout.LayoutParams correctParams = (LinearLayout.LayoutParams) binding.vKzzCorrect.getLayoutParams();
                        LinearLayout.LayoutParams wrongParams = (LinearLayout.LayoutParams) binding.vKzzWrong.getLayoutParams();

                        binding.tvKzzCorrect.setText(String.valueOf(correctKzz));
                        binding.tvKzzTotal.setText(" / " + totalKzz);

                        if (totalKzz > 0) {
                            correctParams.weight = (correctKzz * 100) / totalKzz;
                            wrongParams.weight = (wrongKzz * 100) / totalKzz;
                        } else
                            correctParams.weight = wrongParams.weight = 50;

                        binding.vKzzCorrect.setLayoutParams(correctParams);
                        binding.vKzzWrong.setLayoutParams(wrongParams);

                        break;

                    case 5: // Moj Broj
                        updateRangeBar(binding.tvMinMojBroj, binding.tvMaxMojBroj, binding.rangeBarMojBrojLeft, binding.rangeBarMojBrojFill, binding.rangeBarMojBrojRight, binding.tvRangeMojBroj,
                                gs.getMinPoints(), gs.getMaxPoints(), points, played * 2);

                        int exactNumPct = played != 0 ? (int) ((gs.getMetric("exactMatches") * 100) / (played * 2)) : 0;
                        binding.tvMojBrojPct.setText(String.valueOf(exactNumPct));
                        binding.pbMojBroj.setProgress(exactNumPct, true);
                        break;

                    case 2: // Asocijacije
                        updateRangeBar(binding.tvMinAsoc, binding.tvMaxAsoc, binding.rangeBarAsocLeft, binding.rangeBarAsocFill, binding.rangeBarAsocRight, binding.tvRangeAsoc,
                                gs.getMinPoints(), gs.getMaxPoints(), points, played);

                        long solvedAsoc = gs.getMetric("solved");
                        long unsolvedAsoc = gs.getMetric("unsolved");
                        long totalAsoc = solvedAsoc + unsolvedAsoc;

                        LinearLayout.LayoutParams completeParams = (LinearLayout.LayoutParams) binding.vAsocComplete.getLayoutParams();
                        LinearLayout.LayoutParams incompleteParams = (LinearLayout.LayoutParams) binding.vAsocIncomplete.getLayoutParams();

                        if (totalAsoc > 0) {
                            binding.tvAsocSolved.setText(String.valueOf(solvedAsoc));
                            binding.tvAsocTotal.setText(" / " + totalAsoc);

                            long asocPct = (solvedAsoc * 100) / totalAsoc;

                            completeParams.weight = (solvedAsoc * 100) / totalAsoc;
                            incompleteParams.weight = (unsolvedAsoc * 100) / totalAsoc;
                        } else {
                            completeParams.weight = incompleteParams.weight = 50;
                            binding.tvAsocSolved.setText("0");
                            binding.tvAsocTotal.setText(" / 0");
                        }

                        binding.vAsocComplete.setLayoutParams(completeParams);
                        binding.vAsocIncomplete.setLayoutParams(incompleteParams);

                        break;

                    case 1: // Spojnice
                        updateRangeBar(binding.tvMinSpojnice, binding.tvMaxSpojnice, binding.rangeBarSpojniceLeft, binding.rangeBarSpojniceFill, binding.rangeBarSpojniceRight,
                                binding.tvRangeSpojnice, gs.getMinPoints(), gs.getMaxPoints(), points, played);

                        long correctConn = gs.getMetric("correctGuesses");
                        long totalConn = gs.getMetric("guessesMade");
                        int connPct = totalConn != 0 ? (int) ((correctConn * 100) / totalConn) : 0;
                        binding.tvSpojnicePct.setText(String.valueOf(connPct));
                        binding.pbSpojnice.setProgress(connPct, true);
                        break;

                    case 4: // Korak Po Korak
                        updateRangeBar(binding.tvMinKpk, binding.tvMaxKpk, binding.rangeBarKpkLeft, binding.rangeBarKpkFill, binding.rangeBarKpkRight, binding.tvRangeKpk,
                                gs.getMinPoints(), gs.getMaxPoints(), points, played);

                        TextView[] kpkViews = {
                                binding.tvKpkStep1, binding.tvKpkStep2, binding.tvKpkStep3,
                                binding.tvKpkStep4, binding.tvKpkStep5, binding.tvKpkStep6, binding.tvKpkStep7
                        };

                        LinearProgressIndicator[] kpkPb = {
                                binding.pbKpk1, binding.pbKpk2, binding.pbKpk3,
                                binding.pbKpk4, binding.pbKpk5, binding.pbKpk6, binding.pbKpk7
                        };

                        TextView[] kpkLabels = {
                                binding.tvKpkStepLabel1, binding.tvKpkStepLabel2, binding.tvKpkStepLabel3,
                                binding.tvKpkStepLabel4, binding.tvKpkStepLabel5, binding.tvKpkStepLabel6, binding.tvKpkStepLabel7
                        };

                        int solvedKpk = 0;
                        for (int i = 1; i <= kpkViews.length; i++)
                            solvedKpk += gs.getMetric("guessedInStep" + i);

                        Pair<Integer, Integer> max1 = new Pair<>(0, 0); // <attempt, max>
                        for (int i = 0; i < kpkViews.length; i++) {
                            int stepNum = i + 1;
                            long stepsCount = gs.getMetric("guessedInStep" + stepNum);
                            int pct = solvedKpk != 0 ? (int) (stepsCount * 100) / solvedKpk : 0;

                            max1 = new Pair<>(pct > max1.second ? stepNum : max1.first, Math.max(max1.second, pct));

                            kpkViews[i].setText(pct + "%");
                            kpkPb[i].setProgress(pct, true);
                        }

                        for (int i = 0; i < kpkViews.length; i++) {
                            if (i + 1 == max1.first && max1.second > 0) {
                                kpkViews[i].setTextColor(requireContext().getColor(R.color.accent));
                                kpkLabels[i].setTextColor(requireContext().getColor(R.color.accent));
                            } else {
                                kpkViews[i].setTextColor(requireContext().getColor(R.color.text_mute));
                                kpkLabels[i].setTextColor(requireContext().getColor(R.color.text_mute));
                            }
                        }
                        break;

                    case 3: // Skocko
                        updateRangeBar(binding.tvMinSkocko, binding.tvMaxSkocko, binding.rangeBarSkockoLeft, binding.rangeBarSkockoFill, binding.rangeBarSkockoRight,
                                binding.tvRangeSkocko, gs.getMinPoints(), gs.getMaxPoints(), points, played);

                        TextView[] skockoViews = {
                                binding.tvSkockoAttempt1, binding.tvSkockoAttempt2, binding.tvSkockoAttempt3,
                                binding.tvSkockoAttempt4, binding.tvSkockoAttempt5, binding.tvSkockoAttempt6
                        };

                        LinearProgressIndicator[] skockoPb = {
                                binding.pbSkocko1, binding.pbSkocko2, binding.pbSkocko3,
                                binding.pbSkocko4, binding.pbSkocko5, binding.pbSkocko6
                        };

                        TextView[] skockoLabels = {
                                binding.tvSkockoAttemptLabel1, binding.tvSkockoAttemptLabel2, binding.tvSkockoAttemptLabel3,
                                binding.tvSkockoAttemptLabel4, binding.tvSkockoAttemptLabel5, binding.tvSkockoAttemptLabel6
                        };

                        int solvedSkocko = 0;
                        for (int i = 1; i <= skockoViews.length; i++)
                            solvedSkocko += gs.getMetric("guessedInAttempt" + i);

                        Pair<Integer, Integer> max2 = new Pair<>(0, 0); // <attempt, max>
                        for (int i = 0; i < skockoViews.length; i++) {
                            int attemptNum = i + 1;
                            long attemptsCount = gs.getMetric("guessedInAttempt" + attemptNum);
                            int pct = solvedSkocko != 0 ? (int) (attemptsCount * 100) / solvedSkocko : 0;

                            max2 = new Pair<>(pct > max2.second ? attemptNum : max2.first, Math.max(max2.second, pct));

                            skockoViews[i].setText(pct + "%");
                            skockoPb[i].setProgress(pct, true);
                        }

                        for (int i = 0; i < skockoViews.length; i++) {
                            if (i + 1 == max2.first && max2.second > 0) {
                                skockoViews[i].setTextColor(requireContext().getColor(R.color.accent));
                                skockoLabels[i].setTextColor(requireContext().getColor(R.color.accent));
                            } else {
                                skockoViews[i].setTextColor(requireContext().getColor(R.color.text_mute));
                                skockoLabels[i].setTextColor(requireContext().getColor(R.color.text_mute));
                            }
                        }
                        break;
                }
            }
        });
    }

    private void registerPickerLauncher() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    uploadImageToCloudinary(uri);
                }
        );
    }

    private void uploadImageToCloudinary(Uri imageUri) {
        MediaManager.get().upload(imageUri)
                .unsigned("slagalica-app")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}
                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}
                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String imageUrl = (String) resultData.get("secure_url");
                        viewModel.updateAvatar(imageUrl);
                    }
                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Toast.makeText(requireContext(), "Upload failed: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
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

    private void updateRangeBar(TextView minView, TextView maxView, View leftView, View fillView, View rightView, TextView labelView,
                                int minPoints, int maxPoints, int totalPoints, int totalPlayed) {
        minView.setText(String.valueOf(minPoints));
        maxView.setText(String.valueOf(maxPoints));

        final int BUCKET = 5;
        final int INTERVAL = maxPoints / BUCKET;

        float avgPoints = (float) totalPoints / totalPlayed;
        int bucketMin = (int)(avgPoints / BUCKET) * BUCKET;
        int bucketMax = bucketMin + BUCKET;
        labelView.setText(bucketMin + " – " + bucketMax);

        if (totalPlayed == 0) {
            labelView.setText(0 + " – " + BUCKET);
            setWeights(leftView, fillView, rightView, bucketMin - minPoints, INTERVAL, maxPoints - BUCKET);
            return;
        }

        int leftWeight = bucketMin - minPoints;
        int fillWeight = bucketMax - bucketMin;
        int rightWeight = Math.max(0, maxPoints - bucketMax);

        setWeights(leftView, fillView, rightView, leftWeight, fillWeight, rightWeight);
    }

    private void setWeights(View left, View fill, View right, int lw, int fw, int rw) {
        LinearLayout.LayoutParams lp, fp, rp;
        lp = (LinearLayout.LayoutParams) left.getLayoutParams();
        fp = (LinearLayout.LayoutParams) fill.getLayoutParams();
        rp = (LinearLayout.LayoutParams) right.getLayoutParams();
        lp.weight = lw; fp.weight = fw; rp.weight = rw;
        left.setLayoutParams(lp);
        fill.setLayoutParams(fp);
        right.setLayoutParams(rp);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}