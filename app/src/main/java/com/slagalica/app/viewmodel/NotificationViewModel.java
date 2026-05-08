package com.slagalica.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.slagalica.app.model.NotificationItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotificationViewModel extends ViewModel {

    public enum Filter { ALL, UNREAD }

    private final List<NotificationItem> allItems = new ArrayList<>();

    private final MutableLiveData<List<NotificationItem>> displayedItems =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> unreadCount = new MutableLiveData<>(0);
    private final MutableLiveData<Filter>  activeFilter = new MutableLiveData<>(Filter.ALL);

    public NotificationViewModel() {
        seedFakeData();
        applyFilter(Filter.ALL);
    }

    public LiveData<List<NotificationItem>> getDisplayedItems() { return displayedItems; }
    public LiveData<Integer>  getUnreadCount()    { return unreadCount; }
    public LiveData<Filter>   getActiveFilter()   { return activeFilter; }


    public void setFilter(Filter filter) {
        activeFilter.setValue(filter);
        applyFilter(filter);
    }

    public void markRead(String id) {
        for (NotificationItem item : allItems) {
            if (item.getId().equals(id)) {
                item.setRead(true);
                break;
            }
        }
        refreshCounts();
        applyFilter(activeFilter.getValue() != null ? activeFilter.getValue() : Filter.ALL);
    }

    public void markAllRead() {
        for (NotificationItem item : allItems) item.setRead(true);
        refreshCounts();
        applyFilter(activeFilter.getValue() != null ? activeFilter.getValue() : Filter.ALL);
    }


    private void applyFilter(Filter filter) {
        List<NotificationItem> result = new ArrayList<>();
        for (NotificationItem item : allItems) {
            if (filter == Filter.ALL || !item.isRead()) {
                result.add(item);
            }
        }
        displayedItems.setValue(result);
    }

    private void refreshCounts() {
        int count = 0;
        for (NotificationItem item : allItems) if (!item.isRead()) count++;
        unreadCount.setValue(count);
    }


    private void seedFakeData() {
        long now = System.currentTimeMillis();
        long min = 60_000L;
        long hr  = 3_600_000L;
        long day = 86_400_000L;

        allItems.add(item(NotificationItem.CHANNEL_REWARD,
                "Weekly ranking reward 🏆",
                "You finished 3rd on the weekly leaderboard! +2 tokens added to your account.",
                now - 5 * min, false));

        allItems.add(item(NotificationItem.CHANNEL_OTHER,
                "You moved up to League 2! ⬆️",
                "Congratulations — you now have 100 stars and have entered the Silver League.",
                now - 2 * hr, false));

        allItems.add(item(NotificationItem.CHANNEL_CHAT,
                "New message from Marko",
                "Marko (Šumadija region): 'Anyone up for a game tonight?'",
                now - 4 * hr, false));

        allItems.add(item(NotificationItem.CHANNEL_OTHER,
                "Friend request: ana_92",
                "ana_92 wants to add you as a friend. Tap to respond.",
                now - 6 * hr, true));

        allItems.add(item(NotificationItem.CHANNEL_RANKING,
                "Weekly cycle ending soon",
                "The weekly cycle ends in 2 hours. Play now to secure your ranking!",
                now - 10 * hr, true));

        allItems.add(item(NotificationItem.CHANNEL_OTHER,
                "Daily missions refreshed 📋",
                "New daily missions are available. Complete all 4 for 2 tokens + 3 stars.",
                now - 1 * day, true));

        allItems.add(item(NotificationItem.CHANNEL_REWARD,
                "Monthly ranking reward 🥇",
                "You finished 1st on the monthly leaderboard! +10 tokens added.",
                now - 2 * day, true));

        allItems.add(item(NotificationItem.CHANNEL_CHAT,
                "New message from Stefan",
                "Stefan (Šumadija region): 'Good game yesterday!'",
                now - 3 * day, true));

        allItems.add(item(NotificationItem.CHANNEL_OTHER,
                "Tournament invite",
                "You have been invited to a tournament starting in 30 minutes.",
                now - 3 * day - hr, true));

        allItems.add(item(NotificationItem.CHANNEL_RANKING,
                "Monthly cycle reset",
                "A new monthly cycle has started. Your star count for ranking resets to 0.",
                now - 5 * day, true));

        refreshCounts();
    }

    private static NotificationItem item(String channel, String title,
                                         String body, long ts, boolean read) {
        return new NotificationItem(UUID.randomUUID().toString(),
                channel, title, body, ts, read);
    }
}