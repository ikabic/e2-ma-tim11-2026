package com.slagalica.app.ui.ranking;

import android.app.Dialog;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.airbnb.lottie.LottieAnimationView;
import com.slagalica.app.R;

public class RankingRewardDialog extends Dialog {

    private final int rank;
    private final int tokens;
    private final String cycleType;
    private SoundPool soundPool;
    private int soundId;
    private boolean soundLoaded = false;

    public RankingRewardDialog(@NonNull Context context, int rank, int tokens, String cycleType) {
        super(context, R.style.DialogFullRounded);
        this.rank = rank;
        this.tokens = tokens;
        this.cycleType = cycleType;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_ranking_reward);

        TextView tvTitle   = findViewById(R.id.tvRewardTitle);
        TextView tvSubtitle = findViewById(R.id.tvRewardSubtitle);
        TextView tvTokens  = findViewById(R.id.tvRewardTokens);
        LottieAnimationView lottie = findViewById(R.id.lottieReward);

        String cycleLabel = cycleType.equals("weekly") ? "weekly" : "monthly";
        tvTitle.setText(rankTitle(rank));
        tvSubtitle.setText("You finished " + ordinal(rank) + " place on the " + cycleLabel + " ranking list!");
        tvTokens.setText("+" + tokens + " tokens");

        lottie.playAnimation();

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attrs).build();

        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) {
                soundLoaded = true;
                sp.play(sampleId, 1f, 1f, 1, 0, 1f);
            }
        });

        soundId = soundPool.load(getContext(), R.raw.reward_fanfare, 1);

        findViewById(R.id.btnCloseReward).setOnClickListener(v -> dismiss());

        new Handler(Looper.getMainLooper()).postDelayed(this::dismiss, 8000);
    }

    @Override
    public void dismiss() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        super.dismiss();
    }

    private String rankTitle(int rank) {
        if (rank == 1) return "🥇 First place!";
        if (rank == 2) return "🥈 Second place!";
        if (rank == 3) return "🥉 Third place!";
        return "Top " + rank + "!";
    }

    private String ordinal(int n) {
        if (n == 1) return "1st";
        if (n == 2) return "2nd";
        if (n == 3) return "3rd";
        return n + "th";
    }
}