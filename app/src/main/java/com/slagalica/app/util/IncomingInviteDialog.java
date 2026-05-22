package com.slagalica.app.util;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.slagalica.app.databinding.DialogIncomingInviteBinding;

public class IncomingInviteDialog extends BottomSheetDialogFragment {

    public interface InviteResponseListener {
        void onAccept(String inviteId);
        void onDecline(String inviteId);
    }

    private static final String ARG_INVITE_ID = "inviteId";
    private static final String ARG_FROM_NAME = "fromUsername";
    private static final String ARG_AVATAR_URL = "avatarUrl";

    private DialogIncomingInviteBinding binding;
    private CountDownTimer timer;
    private InviteResponseListener responseListener;

    public static IncomingInviteDialog newInstance(String inviteId, String fromUsername, String avatarUrl) {
        IncomingInviteDialog f = new IncomingInviteDialog();
        Bundle args = new Bundle();
        args.putString(ARG_INVITE_ID, inviteId);
        args.putString(ARG_FROM_NAME, fromUsername);
        args.putString(ARG_AVATAR_URL, avatarUrl);
        f.setArguments(args);
        return f;
    }

    public void setResponseListener(InviteResponseListener listener) {
        this.responseListener = listener;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogIncomingInviteBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        String inviteId = args.getString(ARG_INVITE_ID);
        String fromName = args.getString(ARG_FROM_NAME);
        String avatarUrl = args.getString(ARG_AVATAR_URL);

        binding.tvInviterUsername.setText(fromName);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this).load(avatarUrl).circleCrop().into(binding.ivInviterAvatar);
        }

        timer = new CountDownTimer(10000, 1000) {
            @Override public void onTick(long ms) {
                int sec = (int) (ms / 1000) + 1;
                binding.tvInviteTimerCount.setText(String.valueOf(sec));
                binding.progressInviteTimer.setProgress(sec * 10);
            }
            @Override public void onFinish() {
                if (responseListener != null) responseListener.onDecline(inviteId);
                dismissAllowingStateLoss();
            }
        }.start();

        binding.btnAcceptInvite.setOnClickListener(v -> {
            timer.cancel();
            if (responseListener != null) responseListener.onAccept(inviteId);
            dismissAllowingStateLoss();
        });

        binding.btnDeclineInvite.setOnClickListener(v -> {
            timer.cancel();
            if (responseListener != null) responseListener.onDecline(inviteId);
            dismissAllowingStateLoss();
        });
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
        binding = null;
    }
}