package com.slagalica.app.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.slagalica.app.R;
import com.slagalica.app.databinding.ItemRegionRankingBinding;
import com.slagalica.app.model.Region;
import com.slagalica.app.repository.RegionRepository;

import java.util.ArrayList;
import java.util.List;

public class RegionRankingAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MY_REGION = 0;
    private static final int VIEW_TYPE_DIVIDER = 1;
    private static final int VIEW_TYPE_NORMAL = 2;

    private static final RegionRepository repo = new RegionRepository();
    private List<Region> items = new ArrayList<>();
    private String myRegionKey = "";
    private Region myRegionItem = null;

    public RegionRankingAdapter() {}

    public void submitList(List<Region> newItems, String myRegionKey) {
        this.items = new ArrayList<>(newItems);
        this.myRegionKey = myRegionKey != null ? myRegionKey : "";

        this.myRegionItem = null;
        if (!this.myRegionKey.isEmpty())
            for (Region r : this.items)
                if (r.getRegionKey().equals(this.myRegionKey)) {
                    this.myRegionItem = r;
                    break;
                }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (myRegionItem != null) {
            if (position == 0) return VIEW_TYPE_MY_REGION;
            if (position == 1) return VIEW_TYPE_DIVIDER;
        }
        return VIEW_TYPE_NORMAL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_DIVIDER) {
            View v = new View(parent.getContext());
            int height = (int) (parent.getContext().getResources().getDisplayMetrics().density * 1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
            int marginStartEnd = (int) (parent.getContext().getResources().getDisplayMetrics().density * 18);
            int marginBottom = (int) (parent.getContext().getResources().getDisplayMetrics().density * 16);
            lp.setMargins(marginStartEnd, marginBottom/2, marginStartEnd, marginBottom);
            v.setLayoutParams(lp);
            v.setBackgroundColor(ContextCompat.getColor(parent.getContext(), R.color.border));
            return new DividerVH(v);
        }

        ItemRegionRankingBinding binding = ItemRegionRankingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new RegionVH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        if (viewType == VIEW_TYPE_DIVIDER) return;

        RegionVH regionHolder = (RegionVH) holder;
        if (viewType == VIEW_TYPE_MY_REGION)
            regionHolder.bind(myRegionItem, true);
        else {
            int actualIndex = (myRegionItem != null) ? position - 2 : position;
            regionHolder.bind(items.get(actualIndex), false);
        }
    }

    @Override
    public int getItemCount() {
        if (myRegionItem != null)
            return items.size() + 2;
        return items.size();
    }

    static class DividerVH extends RecyclerView.ViewHolder {
        DividerVH(View itemView) { super(itemView); }
    }

    static class RegionVH extends RecyclerView.ViewHolder {
        final ItemRegionRankingBinding binding;

        RegionVH(ItemRegionRankingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Region info, boolean isPinnedMyRegion) {
            int rank = info.getRank();

            Context context = binding.getRoot().getContext();

            if (rank == 1) {
                binding.tvRegionMedal.setImageResource(R.drawable.ic_top_award);
                binding.tvRegionMedal.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.gold)));
                binding.tvRegionRank.setVisibility(View.GONE);
            } else if (rank == 2) {
                binding.tvRegionMedal.setImageResource(R.drawable.ic_award);
                binding.tvRegionMedal.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.silver)));
                binding.tvRegionRank.setVisibility(View.GONE);
            } else if (rank == 3) {
                binding.tvRegionMedal.setImageResource(R.drawable.ic_award);
                binding.tvRegionMedal.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.bronze)));
                binding.tvRegionRank.setVisibility(View.GONE);
            } else {
                binding.tvRegionMedal.setImageResource(0);
                binding.tvRegionRank.setVisibility(View.VISIBLE);
                binding.tvRegionRank.setText(String.valueOf(rank));
            }

            binding.ivRegionItemIcon.setImageResource(info.getIcon());
            binding.ivRegionItemIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, repo.defaultIconColour(info.getRegionKey()))));
            binding.tvRegionItemName.setText(info.getDisplayName());
            binding.tvRegionItemStars.setText(String.valueOf(info.getCycleStars()));

            if (isPinnedMyRegion)
                binding.getRoot().setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent)));
            else {
                binding.getRoot().setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.border)));
                binding.ivRegionItemIcon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, repo.defaultIconColour(info.getRegionKey()))));
            }
        }
    }
}