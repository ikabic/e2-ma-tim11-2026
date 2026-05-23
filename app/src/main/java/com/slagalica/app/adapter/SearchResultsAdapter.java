package com.slagalica.app.adapter;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.slagalica.app.R;
import com.slagalica.app.databinding.ItemFriendSearchResultBinding;
import com.slagalica.app.model.Friend;

import java.util.ArrayList;
import java.util.List;

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.VH> {

    public interface OnAddFriendClick {
        void onAdd(Friend item);
    }

    private final List<Friend> items = new ArrayList<>();
    private final List<String> friendUids = new ArrayList<>();
    private final OnAddFriendClick listener;

    public SearchResultsAdapter(OnAddFriendClick listener) {
        this.listener = listener;
    }

    public void submitList(List<Friend> newItems, List<String> currentFriendUids) {
        items.clear();
        friendUids.clear();
        if (newItems != null) items.addAll(newItems);
        if (currentFriendUids != null) friendUids.addAll(currentFriendUids);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemFriendSearchResultBinding binding = ItemFriendSearchResultBinding.inflate(inflater, parent, false);

        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Friend item = items.get(pos);
        Context ctx = h.itemView.getContext();

        h.binding.tvResultUsername.setText(item.getUsername());

        if (item.getAvatarUrl() != null && !item.getAvatarUrl().isEmpty()) {
            Glide.with(ctx).load(item.getAvatarUrl()).circleCrop().into(h.binding.ivResultAvatar);
        } else {
            h.binding.ivResultAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
        }

        String league = item.getLeagueName();
        h.binding.tvResultLeague.setText(league + " League");
        applyLeagueBadge(ctx, h, league);

        boolean alreadyFriend = friendUids.contains(item.getUid());
        h.binding.btnAddFriend.setVisibility(alreadyFriend ? View.GONE : View.VISIBLE);
        h.binding.tvAlreadyFriends.setVisibility(alreadyFriend ? View.VISIBLE : View.GONE);

        h.binding.btnAddFriend.setOnClickListener(v -> {
            h.binding.btnAddFriend.setVisibility(View.GONE);
            h.binding.tvAlreadyFriends.setVisibility(View.VISIBLE);
            listener.onAdd(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void applyLeagueBadge(Context ctx, VH h, String league) {
        int color, badge;
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
        h.binding.tvResultLeague.setTextColor(ContextCompat.getColor(ctx, color));
        h.binding.ivResultLeagueBadge.setImageResource(badge);
    }

    static class VH extends RecyclerView.ViewHolder {
        ItemFriendSearchResultBinding binding;

        VH(ItemFriendSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}