package com.slagalica.app.ui.ranking;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

    private final OnProfileClick listener;

    private List<RankingEntry> items = new ArrayList<>();
    private String currentUserId = "";

    public RankingAdapter(OnProfileClick listener) {
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
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ranking, parent, false);
        return new RankViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RankViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    class RankViewHolder extends RecyclerView.ViewHolder {
        private final TextView   tvRank;
        private final ImageView  ivMedal;
        private final TextView   tvUsername;
        private final ImageView  ivLeagueIcon;
        private final TextView   tvLeagueName;
        private final TextView   tvStars;

        RankViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank  = itemView.findViewById(R.id.tvRank);
            ivMedal  = itemView.findViewById(R.id.tvMedal);
            tvUsername  = itemView.findViewById(R.id.tvUsername);
            ivLeagueIcon = itemView.findViewById(R.id.ivLeagueIcon);
            tvLeagueName = itemView.findViewById(R.id.tvLeague);
            tvStars  = itemView.findViewById(R.id.tvStars);
        }

        void bind(RankingEntry entry) {
            int rank = entry.getRank();

            if (rank <= 3) {
                ivMedal.setVisibility(View.VISIBLE);
                tvRank.setVisibility(View.GONE);
                ivMedal.setImageResource(medalDrawable(rank));
            } else {
                ivMedal.setVisibility(View.GONE);
                tvRank.setVisibility(View.VISIBLE);
                tvRank.setText(String.valueOf(rank));
            }

            tvUsername.setText(entry.getUsername());

            tvStars.setText(entry.getCycleStars() + " stars");

            Profile dummy = new Profile();
            dummy.setStars(entry.getTotalStars());
            String league = dummy.getLeague("current");
            ivLeagueIcon.setImageResource(leagueDrawable(league));
            tvLeagueName.setText(league);

            boolean isMe = entry.getUserId().equals(currentUserId);
            itemView.setAlpha(isMe ? 1.0f : 0.92f);
            itemView.setBackgroundResource(isMe ? R.drawable.bg_ranking_me : 0);

            itemView.setOnClickListener(v -> {
                Friend friend = new Friend();
                friend.setUid(entry.getUserId());
                friend.setUsername(entry.getUsername());
                listener.onProfileClick(friend);
            });
        }

        private int leagueDrawable(String league) {
            if (league == null) return R.drawable.league_unranked;
            switch (league) {
                case "Bronze": return R.drawable.league_bronze;
                case "Silver": return R.drawable.league_silver;
                case "Gold": return R.drawable.league_gold;
                case "Platinum": return R.drawable.league_platinum;
                case "Diamond": return R.drawable.league_diamond;
                default:  return R.drawable.league_unranked;
            }
        }

        private int medalDrawable(int rank) {
            return R.drawable.ic_nav_ranks;
        }
    }
}