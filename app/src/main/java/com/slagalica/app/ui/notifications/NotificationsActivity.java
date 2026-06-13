package com.slagalica.app.ui.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.slagalica.app.BaseActivity;
import com.slagalica.app.R;
import com.slagalica.app.model.NotificationItem;
import com.slagalica.app.viewmodel.NotificationViewModel;
import com.slagalica.app.viewmodel.NotificationViewModel.Filter;

public class NotificationsActivity extends BaseActivity implements NotificationsAdapter.OnMarkReadListener, NotificationsAdapter.OnNotifClickListener {

    private NotificationViewModel viewModel;
    private NotificationsAdapter adapter;
    private MaterialButton btnFilterAll, btnFilterUnread, btnMarkAllRead, btnBack;
    private RecyclerView rvNotifications;
    private View layoutEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        btnBack = findViewById(R.id.btnBack);
        btnMarkAllRead = findViewById(R.id.btnMarkAllRead);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterUnread = findViewById(R.id.btnFilterUnread);
        rvNotifications = findViewById(R.id.rvNotifications);
        layoutEmpty = findViewById(R.id.layoutEmpty);

        adapter = new NotificationsAdapter(this, this);
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(NotificationViewModel.class);

        viewModel.getDisplayedItems().observe(this, items -> {
            adapter.submitList(items);
            boolean empty = items == null || items.isEmpty();
            rvNotifications.setVisibility(empty ? View.GONE : View.VISIBLE);
            layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        });

        viewModel.getActiveFilter().observe(this, filter -> {
            boolean allActive = filter == Filter.ALL;
            setChipActive(btnFilterAll, allActive);
            setChipActive(btnFilterUnread, !allActive);
        });

        viewModel.getUnreadCount().observe(this, count -> {
            String label = count != null && count > 0 ? "Unread (" + count + ")" : "Unread";
            btnFilterUnread.setText(label);
        });

        btnBack.setOnClickListener(v -> finish());

        btnMarkAllRead.setOnClickListener(v -> viewModel.markAllRead());

        btnFilterAll.setOnClickListener(v -> viewModel.setFilter(Filter.ALL));

        btnFilterUnread.setOnClickListener(v -> viewModel.setFilter(Filter.UNREAD));
    }

    @Override
    public void onMarkRead(String notificationId) {
        viewModel.markRead(notificationId);
    }

    private void setChipActive(MaterialButton btn, boolean active) {
        if (active) {
            btn.setBackgroundTintList(getResources().getColorStateList(R.color.accent, null));
            btn.setTextColor(getResources().getColor(R.color.accent_ink, null));
            btn.setStrokeColor(getResources().getColorStateList(R.color.accent, null));
        } else {
            btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
            btn.setTextColor(getResources().getColor(R.color.text_mute, null));
            btn.setStrokeColor(getResources().getColorStateList(R.color.border, null));
        }
    }

    @Override
    public void onChatNotifClick(NotificationItem item) {
        if (!item.isRead()) viewModel.markRead(item.getId());

        Intent i = new Intent(this, com.slagalica.app.ui.chat.ChatActivity.class);
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String regionKey = doc.getString("region");
                    String username  = doc.getString("username");
                    if (regionKey != null) {
                        i.putExtra("regionKey", regionKey != null ? regionKey : "");
                        i.putExtra("username",  username  != null ? username  : "");
                        startActivity(i);
                    }
                });
    }

    @Override
    public void onRankingNotifClick(NotificationItem item) {
        if (!item.isRead()) viewModel.markRead(item.getId());

        Intent i = new Intent(this, com.slagalica.app.ui.HomeActivity.class);
        i.putExtra("openTab", 2);
        if (NotificationItem.CHANNEL_REWARD.equals(item.getChannel())) {
            i.putExtra("showRewardDialog", true);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }
}