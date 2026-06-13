package com.slagalica.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.slagalica.app.R;
import com.slagalica.app.model.Challenge;

import java.util.ArrayList;
import java.util.List;

public class ChallengeListAdapter extends RecyclerView.Adapter<ChallengeListAdapter.VH> {

    public interface OnJoinListener { void onJoin(Challenge challenge); }

    private final List<Challenge> items = new ArrayList<>();
    private final OnJoinListener listener;

    public ChallengeListAdapter(OnJoinListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Challenge> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_challenge, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Challenge c = items.get(position);
        h.creator.setText(c.getCreatorUsername());
        h.stars.setText(String.valueOf(c.getStarsStake()));
        h.tokens.setText(String.valueOf(c.getTokensStake()));
        h.players.setText(c.getPlayerCount() + "/4");
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onJoin(c);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView creator, stars, tokens, players;
        VH(View v) {
            super(v);
            creator = v.findViewById(R.id.tvCreator);
            stars = v.findViewById(R.id.tvStakeStars);
            tokens = v.findViewById(R.id.tvStakeTokens);
            players = v.findViewById(R.id.tvPlayers);
        }
    }
}
