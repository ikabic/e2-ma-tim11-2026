package com.slagalica.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.slagalica.app.R;
import com.slagalica.app.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private static final int TYPE_LEFT = 0;
    private static final int TYPE_RIGHT = 1;

    private final List<ChatMessage> items = new ArrayList<>();
    private final String myUid;
    private final SimpleDateFormat formatter = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());

    public ChatAdapter(String myUid) {
        this.myUid = myUid;
    }

    public void setItems(List<ChatMessage> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage m = items.get(position);
        return (myUid != null && myUid.equals(m.getSenderUid())) ? TYPE_RIGHT : TYPE_LEFT;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_RIGHT ? R.layout.item_chat_right : R.layout.item_chat_left;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage m = items.get(position);
        holder.tvSenderName.setText(m.getSenderName());
        holder.tvMessageText.setText(m.getText());
        holder.tvMessageTime.setText(formatter.format(new Date(m.getTimestamp())));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSenderName;
        final TextView tvMessageText;
        final TextView tvMessageTime;

        MessageViewHolder(View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessageText = itemView.findViewById(R.id.tvMessageText);
            tvMessageTime = itemView.findViewById(R.id.tvMessageTime);
        }
    }
}
