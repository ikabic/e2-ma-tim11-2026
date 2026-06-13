package com.slagalica.app.ui.notifications;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.slagalica.app.R;
import com.slagalica.app.model.NotificationItem;

import java.util.ArrayList;
import java.util.List;

public class NotificationsAdapter extends
        RecyclerView.Adapter<NotificationsAdapter.NotifViewHolder> {

    public interface OnMarkReadListener {
        void onMarkRead(String notificationId);
    }

    public interface OnNotifClickListener {
        void onChatNotifClick(NotificationItem item);
        void onRankingNotifClick(NotificationItem item);
    }

    private List<NotificationItem> items = new ArrayList<>();
    private OnMarkReadListener listener;
    private OnNotifClickListener clickListener;

    public NotificationsAdapter(OnMarkReadListener markReadListener, OnNotifClickListener clickListener) {
        this.listener = markReadListener;
        this.clickListener = clickListener;
    }

    public void submitList(List<NotificationItem> newItems) {
        this.items = new ArrayList<>(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotifViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new NotifViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull NotifViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    class NotifViewHolder extends RecyclerView.ViewHolder {

        private final View viewUnreadDot;
        private final ImageView ivChannelIcon;
        private final TextView tvTitle;
        private final TextView tvBody;
        private final TextView tvTime;
        private final MaterialButton btnMarkRead;

        NotifViewHolder(@NonNull View itemView) {
            super(itemView);
            viewUnreadDot = itemView.findViewById(R.id.viewUnreadDot);
            ivChannelIcon = itemView.findViewById(R.id.tvChannelIcon);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvBody = itemView.findViewById(R.id.tvBody);
            tvTime = itemView.findViewById(R.id.tvTime);
            btnMarkRead = itemView.findViewById(R.id.btnMarkRead);
        }

        void bind(NotificationItem item) {
            ivChannelIcon.setImageResource(channelIcon(item.getChannel()));

            tvTitle.setText(item.getTitle());
            tvTitle.setTextColor(itemView.getContext().getResources().getColor(item.isRead() ? R.color.text_mute : R.color.text, null));

            tvBody.setText(item.getBody());
            tvTime.setText(relativeTime(item.getTimestampMs()));

            viewUnreadDot.setVisibility(item.isRead() ? View.INVISIBLE : View.VISIBLE);

            if (item.isRead()) {
                btnMarkRead.setVisibility(View.GONE);
            } else {
                btnMarkRead.setVisibility(View.VISIBLE);
                btnMarkRead.setOnClickListener(v -> {
                    if (listener != null) listener.onMarkRead(item.getId());
                });
            }

            String channel = item.getChannel();
            if (NotificationItem.CHANNEL_CHAT.equals(channel)) {
                itemView.setOnClickListener(v -> {
                    if (clickListener != null) clickListener.onChatNotifClick(item);
                });
            } else if (NotificationItem.CHANNEL_RANKING.equals(channel)
                    || NotificationItem.CHANNEL_REWARD.equals(channel)
                    || NotificationItem.CHANNEL_OTHER.equals(channel)) {
                itemView.setOnClickListener(v -> {
                    if (clickListener != null) clickListener.onRankingNotifClick(item);
                });
            } else {
                itemView.setOnClickListener(null);
            }
        }

        private int channelIcon(String channel) {
            if (channel == null) return R.drawable.ic_notifications;
            switch (channel) {
                case NotificationItem.CHANNEL_RANKING:
                case NotificationItem.CHANNEL_REWARD:
                case NotificationItem.CHANNEL_MATCH:
                    return R.drawable.ic_nav_ranks;
                case NotificationItem.CHANNEL_CHAT:
                case NotificationItem.CHANNEL_OTHER:
                default:
                    return R.drawable.ic_notifications;
            }
        }

        private String relativeTime(long timestampMs) {
            long diffMs  = System.currentTimeMillis() - timestampMs;
            long diffSec = diffMs / 1000;
            long diffMin = diffSec / 60;
            long diffHr  = diffMin / 60;
            long diffDay = diffHr  / 24;

            if (diffSec < 60) return "just now";
            if (diffMin < 60) return diffMin + " min";
            if (diffHr  < 24) return diffHr  + " h";
            return diffDay + " d";
        }
    }
}