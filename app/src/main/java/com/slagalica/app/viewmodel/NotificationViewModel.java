package com.slagalica.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.slagalica.app.model.NotificationItem;
import com.slagalica.app.repository.NotificationRepository;
import com.slagalica.app.repository.RepositoryCallback;

import java.util.ArrayList;
import java.util.List;

public class NotificationViewModel extends ViewModel {

    public enum Filter { ALL, UNREAD }

    private final NotificationRepository repo = new NotificationRepository();
    private final List<NotificationItem> allItems = new ArrayList<>();

    private final MutableLiveData<List<NotificationItem>> displayedItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> unreadCount  = new MutableLiveData<>(0);
    private final MutableLiveData<Filter>  activeFilter = new MutableLiveData<>(Filter.ALL);

    public NotificationViewModel() {
        loadFromFirestore();
    }

    public LiveData<List<NotificationItem>> getDisplayedItems() { return displayedItems; }
    public LiveData<Integer>  getUnreadCount()  { return unreadCount; }
    public LiveData<Filter>   getActiveFilter() { return activeFilter; }

    public void setFilter(Filter filter) {
        activeFilter.setValue(filter);
        applyFilter(filter);
    }

    public void markRead(String id) {
        repo.markRead(id, new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void result) {
                for (NotificationItem item : allItems)
                    if (item.getId().equals(id)) { item.setRead(true); break; }
                refreshCounts();
                applyFilter(activeFilter.getValue() != null
                        ? activeFilter.getValue() : Filter.ALL);
            }
            @Override public void onFailure(Exception e) { /* ignore */ }
        });
    }

    public void markAllRead() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;
        repo.markAllRead(uid, new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void result) {
                for (NotificationItem item : allItems) item.setRead(true);
                refreshCounts();
                applyFilter(activeFilter.getValue() != null
                        ? activeFilter.getValue() : Filter.ALL);
            }
            @Override public void onFailure(Exception e) { /* ignore */ }
        });
    }

    public void reload() { loadFromFirestore(); }

    private void loadFromFirestore() {
        repo.fetchAll(new RepositoryCallback<List<NotificationItem>>() {
            @Override public void onSuccess(List<NotificationItem> result) {
                allItems.clear();
                allItems.addAll(result);
                refreshCounts();
                applyFilter(activeFilter.getValue() != null
                        ? activeFilter.getValue() : Filter.ALL);
            }
            @Override public void onFailure(Exception e) {
            }
        });
    }

    private void applyFilter(Filter filter) {
        List<NotificationItem> result = new ArrayList<>();
        for (NotificationItem item : allItems) {
            if (filter == Filter.ALL || !item.isRead()) result.add(item);
        }
        displayedItems.setValue(result);
    }

    private void refreshCounts() {
        int count = 0;
        for (NotificationItem item : allItems) if (!item.isRead()) count++;
        unreadCount.setValue(count);
    }
}