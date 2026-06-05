package com.slagalica.app.ui.ranking;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.slagalica.app.R;
import com.slagalica.app.adapter.FriendsAdapter;
import com.slagalica.app.model.Friend;
import com.slagalica.app.model.RankingEntry;
import com.slagalica.app.model.Profile;

import java.util.ArrayList;
import java.util.List;

public class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.RankViewHolder> {

    public interface OnProfileClick {
        void onProfileClick(Friend friend);
    }

    private final RankingAdapter.OnProfileClick listener;

    private List<RankingEntry> items = new ArrayList<>();
    private String currentUserId = "";

    public RankingAdapter(RankingAdapter.OnProfileClick listener) {
        this.listener = listener;

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        }
    }

    public void submitList(List<RankingEntry> newItems) {
        this.items = new ArrayList<>(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RankViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ranking, parent, false);
        return new RankViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RankViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    class RankViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvRank;
        private final TextView tvMedal;
        private final TextView tvUsername;
        private final TextView tvLeague;
        private final TextView tvStars;

        RankViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRank);
            tvMedal = itemView.findViewById(R.id.tvMedal);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvLeague = itemView.findViewById(R.id.tvLeague);
            tvStars = itemView.findViewById(R.id.tvStars);
        }

        void bind(RankingEntry entry) {
            int rank = entry.getRank();

            if (rank == 1) { tvMedal.setText("🥇"); tvRank.setVisibility(View.GONE); }
            else if (rank == 2) { tvMedal.setText("🥈"); tvRank.setVisibility(View.GONE); }
            else if (rank == 3) { tvMedal.setText("🥉"); tvRank.setVisibility(View.GONE); }
            else {
                tvMedal.setText("");
                tvRank.setVisibility(View.VISIBLE);
                tvRank.setText(String.valueOf(rank));
            }

            tvUsername.setText(entry.getUsername());
            tvStars.setText("⭐ " + entry.getCycleStars());

            Profile dummy = new Profile();
            dummy.setStars(entry.getTotalStars());
            tvLeague.setText(leagueEmoji(dummy.getLeague("current")) + " " + dummy.getLeague("current"));

            boolean isMe = entry.getUserId().equals(currentUserId);
            itemView.setAlpha(isMe ? 1.0f : 0.92f);
            int bgRes = isMe ? R.drawable.bg_hero : 0;
            itemView.setBackgroundResource(isMe ? R.drawable.bg_ranking_me : 0);

            itemView.setOnClickListener(v -> {
                Friend friend = new Friend();
                friend.setUid(entry.getUserId());
                friend.setUsername(entry.getUsername());

                listener.onProfileClick(friend);
            });
        }

        private String leagueEmoji(String league) {
            if (league == null) return "❓";
            switch (league) {
                case "Bronze":   return "🥉";
                case "Silver":   return "🥈";
                case "Gold":     return "🥇";
                case "Platinum": return "💎";
                case "Diamond":  return "💠";
                default:         return "🔰";
            }
        }
    }
}