package com.slagalica.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.slagalica.app.R;
import com.slagalica.app.databinding.ItemFriendBinding;
import com.slagalica.app.model.Friend;
import com.slagalica.app.util.ProfileUtils;

import java.util.ArrayList;
import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.VH> {

    public interface OnChallengeClick { void onChallenge(Friend friend); }

    private static final String PAYLOAD_STATUS = "status";

    private final OnChallengeClick listener;

    private final DiffUtil.ItemCallback<Friend> DIFF_CALLBACK = new DiffUtil.ItemCallback<Friend>() {
        @Override
        public boolean areItemsTheSame(@NonNull Friend oldItem, @NonNull Friend newItem) {
            return oldItem.getUid().equals(newItem.getUid());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Friend oldItem, @NonNull Friend newItem) {
            return oldItem.isOnline() == newItem.isOnline()
                    && oldItem.isInGame() == newItem.isInGame()
                    && oldItem.getUsername().equals(newItem.getUsername())
                    && oldItem.getStars() == newItem.getStars()
                    && oldItem.getMonthlyRank() == newItem.getMonthlyRank()
                    && oldItem.getLeagueName().equals(newItem.getLeagueName());
        }

        @Nullable
        @Override
        public Object getChangePayload(@NonNull Friend oldItem, @NonNull Friend newItem) {
            boolean statusChanged = oldItem.isOnline() != newItem.isOnline() || oldItem.isInGame() != newItem.isInGame();
            boolean otherChanged = !oldItem.getUsername().equals(newItem.getUsername()) || oldItem.getStars() != newItem.getStars()
                    || oldItem.getMonthlyRank() != newItem.getMonthlyRank() || !oldItem.getLeagueName().equals(newItem.getLeagueName());

            if (statusChanged && !otherChanged) {
                return PAYLOAD_STATUS;
            }
            return null;
        }
    };

    private final AsyncListDiffer<Friend> differ = new AsyncListDiffer<>(this, DIFF_CALLBACK);

    public FriendsAdapter(OnChallengeClick listener) {
        this.listener = listener;
    }

    public void submitList(List<Friend> newItems) {
        differ.submitList(newItems != null ? new ArrayList<>(newItems) : new ArrayList<>());
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFriendBinding binding = ItemFriendBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && PAYLOAD_STATUS.equals(payloads.get(0))) {
            bindStatus(h, differ.getCurrentList().get(pos));
            return;
        }
        super.onBindViewHolder(h, pos, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Friend item = differ.getCurrentList().get(pos);
        Context ctx = h.itemView.getContext();

        h.binding.tvFriendUsername.setText(item.getUsername());
        h.binding.tvFriendStars.setText(item.getStars() + " stars");

        String league = item.getLeagueName();
        h.binding.tvFriendLeague.setText(league + " League");
        applyLeagueStyle(ctx, h, league);

        if (item.getMonthlyRank() > 0) {
            h.binding.tvFriendRank.setText("#" + item.getMonthlyRank() + " this month");
        } else {
            h.binding.tvFriendRank.setText("Unranked");
        }

        if (item.getAvatarUrl() != null && !item.getAvatarUrl().isEmpty()) {
            Glide.with(ctx).load(item.getAvatarUrl()).circleCrop().into(h.binding.ivFriendAvatar);
        } else {
            h.binding.ivFriendAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
        }

        ProfileUtils.applyRegionFrame(h.binding.ivFriendRegionAwardFrame, item.getPrevCycleRegionRank(), h.binding.ivFriendAvatar);

        bindStatus(h, item);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    private void bindStatus(@NonNull VH h, Friend item) {
        h.binding.viewOnlineDot.setVisibility(item.isOnline() ? View.VISIBLE : View.GONE);
        h.binding.chipInGame.setVisibility(item.isInGame() ? View.VISIBLE : View.GONE);

        boolean canPlay = item.isOnline() && !item.isInGame();
        h.binding.btnChallenge.setEnabled(canPlay);
        h.binding.btnChallenge.setAlpha(canPlay ? 1f : 0.45f);

        if (canPlay) {
            h.binding.btnChallenge.setText("Play");
            h.binding.btnChallenge.setVisibility(View.VISIBLE);
            h.binding.btnChallenge.setOnClickListener(v -> listener.onChallenge(item));
        } else {
            h.binding.btnChallenge.setVisibility(item.isInGame() ? View.VISIBLE : View.GONE);
            h.binding.btnChallenge.setText(item.isInGame() ? "In game" : "Play");
            h.binding.btnChallenge.setOnClickListener(null);
        }
    }

    private void applyLeagueStyle(Context ctx, VH h, String league) {
        int color;
        int badge;
        switch (league) {
            case "Bronze":
                color = R.color.league_bronze;
                badge = R.drawable.league_bronze;
                break;
            case "Silver":
                color = R.color.league_silver;
                badge = R.drawable.league_silver;
                break;
            case "Gold":
                color = R.color.league_gold;
                badge = R.drawable.league_gold;
                break;
            case "Platinum":
                color = R.color.league_platinum;
                badge = R.drawable.league_platinum;
                break;
            case "Diamond":
                color = R.color.league_diamond;
                badge = R.drawable.league_diamond;
                break;
            default:
                color = R.color.league_unranked;
                badge = R.drawable.league_unranked;
                break;
        }
        h.binding.tvFriendLeague.setTextColor(ContextCompat.getColor(ctx, color));
        h.binding.ivFriendLeagueBadge.setImageResource(badge);
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemFriendBinding binding;

        VH(ItemFriendBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}