package com.slagalica.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.slagalica.app.R;
import com.slagalica.app.repository.ChallengeRepository.Participant;

import java.util.ArrayList;
import java.util.List;

public class ChallengeParticipantAdapter extends RecyclerView.Adapter<ChallengeParticipantAdapter.VH> {

    private final List<Participant> items = new ArrayList<>();
    private String status = "open";
    private String winnerUid;
    private String secondUid;

    public void setData(List<Participant> ps, String status, String winnerUid, String secondUid) {
        items.clear();
        if (ps != null) items.addAll(ps);
        this.status = status != null ? status : "open";
        this.winnerUid = winnerUid;
        this.secondUid = secondUid;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_challenge_participant, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Participant p = items.get(position);
        h.name.setText(p.username);

        if ("finished".equals(status)) {
            h.status.setText(p.score + " pts");
            if (p.uid != null && p.uid.equals(winnerUid)) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setImageResource(R.drawable.ic_trophy);
                h.status.setText(p.score + " pts  •  winner");
            } else if (p.uid != null && p.uid.equals(secondUid)) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setImageResource(R.drawable.ic_award);
            } else {
                h.badge.setVisibility(View.GONE);
            }
        } else if ("playing".equals(status)) {
            h.badge.setVisibility(View.GONE);
            h.status.setText(p.played ? "Played" : "Playing…");
        } else {
            h.badge.setVisibility(View.GONE);
            h.status.setText("Ready");
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, status;
        final ImageView badge;
        VH(View v) {
            super(v);
            name = v.findViewById(R.id.tvName);
            status = v.findViewById(R.id.tvStatus);
            badge = v.findViewById(R.id.ivBadge);
        }
    }
}
