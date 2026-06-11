package com.slagalica.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.database.ChildEventListener;
import com.slagalica.app.model.ChatMessage;
import com.slagalica.app.repository.ChatRepository;
import com.slagalica.app.repository.RepositoryCallback;

import java.util.ArrayList;
import java.util.List;

public class ChatViewModel extends ViewModel {

    private final ChatRepository repository = new ChatRepository();
    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());

    private ChildEventListener listener;
    private String regionKey;

    public LiveData<List<ChatMessage>> getMessages() { return messages; }

    public String getUid() { return repository.getUid(); }

    public void start(String regionKey) {
        if (listener != null || regionKey == null) return;
        this.regionKey = regionKey;
        listener = repository.listenForMessages(regionKey, msg -> {
            List<ChatMessage> list = messages.getValue();
            if (list == null) list = new ArrayList<>();
            list.add(msg);
            messages.setValue(list);
        });
    }

    public void send(String senderName, String text) {
        if (regionKey == null || text == null || text.trim().isEmpty()) return;
        repository.sendMessage(regionKey, senderName, text.trim(), new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void v) {}
            @Override public void onFailure(Exception e) {}
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (listener != null && regionKey != null) repository.removeListener(regionKey, listener);
    }
}
