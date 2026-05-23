package com.slagalica.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.slagalica.app.BaseActivity;
import com.slagalica.app.adapter.FriendsAdapter;
import com.slagalica.app.adapter.SearchResultsAdapter;
import com.slagalica.app.databinding.ActivityFriendsBinding;
import com.slagalica.app.model.Friend;
import com.slagalica.app.ui.match.MatchmakingActivity;
import com.slagalica.app.viewmodel.FriendsViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FriendsActivity extends BaseActivity {

    private ActivityFriendsBinding binding;
    private FriendsViewModel viewModel;
    private FriendsAdapter friendsAdapter;
    private SearchResultsAdapter searchAdapter;
    private CountDownTimer outgoingTimer;

    private String resolvedMatchId;
    private String username = "Player";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityFriendsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            Toast.makeText(this, "Register to get access to friends", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupAdapters();
        setupViewModel();
        setupSearch();
        setupQrScan();
        setupOutgoingBanner();

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("username");
                    if (name != null && !name.isEmpty()) username = name;
                });

        viewModel.loadFriends();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (outgoingTimer != null) outgoingTimer.cancel();
    }

    private void setupAdapters() {
        friendsAdapter = new FriendsAdapter(friend -> viewModel.sendInvite(friend));

        binding.rvFriends.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFriends.setAdapter(friendsAdapter);

        searchAdapter = new SearchResultsAdapter(item -> viewModel.addFriend(item.getUid()));
        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSearchResults.setAdapter(searchAdapter);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(FriendsViewModel.class);

        viewModel.getFriends().observe(this, friends -> {
            binding.tvFriendCount.setText(String.valueOf(friends.size()));
            binding.sectionEmpty.setVisibility(friends.isEmpty() ? View.VISIBLE : View.GONE);
            friendsAdapter.submitList(friends);
        });

        viewModel.getSearchResults().observe(this, results -> {
            if (results == null) {
                binding.sectionSearchResults.setVisibility(View.GONE);
                return;
            }
            binding.sectionSearchResults.setVisibility(View.VISIBLE);
            List<Friend> currentFriends = viewModel.getFriends().getValue();
            List<String> friendUids = currentFriends != null ? currentFriends.stream().map(Friend::getUid).collect(Collectors.toList()) : new ArrayList<>();
            searchAdapter.submitList(results, friendUids);
        });

        viewModel.getError().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });

        viewModel.getPendingInviteId().observe(this, id -> {
            boolean hasPending = id != null;
            binding.bannerPendingInvite.setVisibility(hasPending ? View.VISIBLE : View.GONE);
            if (hasPending) startOutgoingCountdown();
            else stopOutgoingCountdown();
        });

        viewModel.getPendingMatchId().observe(this, id -> {
            if (id != null) resolvedMatchId = id;
        });

        viewModel.getInviteResult().observe(this, result -> {
            if (result == null) return;
            switch (result) {
                case "accepted":
                    String matchId = viewModel.getPendingMatchId().getValue();

                    if (matchId == null) {
                        Toast.makeText(this, "Match setup failed, try again", Toast.LENGTH_SHORT).show();
                        break;
                    }

                    Intent intent = new Intent(this, MatchmakingActivity.class);
                    intent.putExtra("inviteMatchId", matchId);
                    intent.putExtra("isPlayer1", true);
                    intent.putExtra("opponentUsername", viewModel.getPendingInviteUsername().getValue());
                    intent.putExtra("username", username);
                    startActivity(intent);

                    viewModel.clearPendingInvite();

                    break;
                case "declined":
                    Toast.makeText(this, "Invite was declined", Toast.LENGTH_SHORT).show();
                    break;
                case "expired":
                    Toast.makeText(this, "Invite expired — no response", Toast.LENGTH_SHORT).show();
                    break;
                case "cancelled":
                    break;
            }
            viewModel.clearInviteResult();
        });
    }

    private void setupSearch() {
        binding.btnSearch.setOnClickListener(v -> doSearch());

        binding.etSearch.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(); return true; }
            return false;
        });

        binding.etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (TextUtils.isEmpty(s)) viewModel.clearSearch();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void doSearch() {
        String query = binding.etSearch.getText() != null ? binding.etSearch.getText().toString().trim() : "";

        if (query.isEmpty()) {
            viewModel.clearSearch();
            return;
        }

        viewModel.searchUsers(query);
    }

    private void setupQrScan() {
        binding.btnScanQr.setOnClickListener(v -> qrScanLauncher.launch(
                new com.journeyapps.barcodescanner.ScanOptions()
                        .setPrompt("Scan a friend's QR code")
                        .setBeepEnabled(true)
                        .setBarcodeImageEnabled(false)
                        .setOrientationLocked(true)
        ));
    }

    private final androidx.activity.result.ActivityResultLauncher<com.journeyapps.barcodescanner.ScanOptions>
            qrScanLauncher = registerForActivityResult(
            new com.journeyapps.barcodescanner.ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    viewModel.addFriend(result.getContents());
                    Toast.makeText(this, "Friend added!", Toast.LENGTH_SHORT).show();
                }
            });

    private void setupOutgoingBanner() {
        binding.btnCancelInvite.setOnClickListener(v -> {
            stopOutgoingCountdown();
            viewModel.cancelInvite();
        });
    }

    private void startOutgoingCountdown() {
        stopOutgoingCountdown();
        outgoingTimer = new CountDownTimer(10_000, 1_000) {
            @Override public void onTick(long ms) {
                binding.tvInviteCountdown.setText(String.valueOf((int)(ms / 1000) + 1));
            }
            @Override public void onFinish() {
                viewModel.expireInvite();
                Toast.makeText(FriendsActivity.this, "No response — invite expired", Toast.LENGTH_SHORT).show();
            }
        }.start();
    }

    private void stopOutgoingCountdown() {
        if (outgoingTimer != null) {
            outgoingTimer.cancel();
            outgoingTimer = null;
        }
    }

    @Override
    public void onAccept(String inviteId) {
        super.onAccept(inviteId);
        viewModel.acceptInvite(inviteId);
    }

    @Override
    public void onDecline(String inviteId) {
        super.onDecline(inviteId);
        viewModel.declineInvite(inviteId);
    }
}