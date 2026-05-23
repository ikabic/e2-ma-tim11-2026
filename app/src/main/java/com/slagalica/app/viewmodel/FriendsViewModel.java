package com.slagalica.app.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.slagalica.app.model.Friend;
import com.slagalica.app.repository.FriendsRepository;
import com.slagalica.app.repository.MatchRepository;
import com.slagalica.app.repository.RepositoryCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FriendsViewModel extends ViewModel {

    private final FriendsRepository friendsRepository;
    private final MatchRepository matchRepository;

    private final MutableLiveData<List<Friend>> friends = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Friend>> searchResults = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> pendingInviteId = new MutableLiveData<>();
    private final MutableLiveData<String> pendingInviteUsername = new MutableLiveData<>();
    private final MutableLiveData<String> pendingMatchId = new MutableLiveData<>();
    private final MutableLiveData<String> inviteResult = new MutableLiveData<>();   // "accepted"|"declined"|"cancelled"|"expired"

    private static final FirebaseDatabase rtdb = FirebaseDatabase.getInstance("https://slagalica-66578-default-rtdb.europe-west1.firebasedatabase.app/");

    private ValueEventListener outgoingListener;
    private DatabaseReference outgoingRef;

    public FriendsViewModel() {
        friendsRepository = new FriendsRepository();
        matchRepository = new MatchRepository();
    }

    public LiveData<List<Friend>> getFriends() { return friends; }
    public LiveData<List<Friend>> getSearchResults() { return searchResults; }
    public LiveData<String> getError() { return error; }

    public LiveData<String> getPendingMatchId() { return pendingMatchId; }

    private final Map<String, ValueEventListener> presenceListeners = new HashMap<>();

    public void loadFriends() {
        loading.setValue(true);
        friendsRepository.loadFriends(new RepositoryCallback<List<Friend>>() {
            @Override public void onSuccess(List<Friend> result) {
                loading.setValue(false);
                friends.setValue(result);
                attachPresenceListeners(result);
            }
            @Override public void onFailure(Exception e) {
                loading.setValue(false);
                error.setValue(e.getMessage());
            }
        });
    }

    private void attachPresenceListeners(List<Friend> friendList) {
        for (Map.Entry<String, ValueEventListener> entry : presenceListeners.entrySet()) {
            rtdb.getReference("presence/" + entry.getKey()).removeEventListener(entry.getValue());
        }
        presenceListeners.clear();

        for (Friend friend : friendList) {
            DatabaseReference ref = rtdb.getReference("presence/" + friend.getUid());

            ValueEventListener listener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String status = snapshot.child("status").getValue(String.class);
                    Boolean inGame = snapshot.child("inGame").getValue(Boolean.class);

                    List<Friend> current = friends.getValue();
                    if (current == null) return;

                    List<Friend> updated = new ArrayList<>(current);
                    for (int i = 0; i < updated.size(); i++) {
                        if (updated.get(i).getUid().equals(friend.getUid())) {
                            Friend f = updated.get(i).withStatus(status, inGame);
                            updated.set(i, f);
                            break;
                        }
                    }
                    friends.postValue(updated);
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            };

            ref.addValueEventListener(listener);
            presenceListeners.put(friend.getUid(), listener);
        }
    }

    public void searchUsers(String query) {
        List<Friend> current = friends.getValue();
        List<String> friendUids = current != null ? current.stream().map(Friend::getUid).collect(Collectors.toList()) : new ArrayList<>();

        friendsRepository.searchByUsername(query, friendUids, new RepositoryCallback<List<Friend>>() {
            @Override public void onSuccess(List<Friend> result) { searchResults.setValue(result); }
            @Override public void onFailure(Exception e) { error.setValue(e.getMessage()); }
        });
    }

    public void clearSearch() { searchResults.setValue(null); }

    public void addFriend(String targetUid) {
        friendsRepository.addFriend(targetUid, new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void v) { loadFriends(); }
            @Override public void onFailure(Exception e) { error.setValue(e.getMessage()); }
        });
    }

    public LiveData<String> getPendingInviteId() { return pendingInviteId; }
    public LiveData<String> getPendingInviteUsername() { return pendingInviteUsername; }
    public LiveData<String> getInviteResult() { return inviteResult; }

    public void sendInvite(Friend friend) {
        String myUid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (myUid == null) { error.setValue("Not logged in"); return; }

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(myUid).get()
                .addOnSuccessListener(doc -> {
                    String myUsername = doc.getString("username");
                    if (myUsername == null) myUsername = "Player";
                    final String resolvedUsername = myUsername;

                    friendsRepository.sendInvite(friend.getUid(), friend.getUsername(), resolvedUsername,
                            new RepositoryCallback<String>() {
                                @Override public void onSuccess(String inviteId) {
                                    pendingInviteId.setValue(inviteId);
                                    listenToOutgoingInvite(inviteId);
                                }
                                @Override public void onFailure(Exception e) { error.setValue(e.getMessage()); }
                            });
                })
                .addOnFailureListener(e -> error.setValue(e.getMessage()));
    }

    public void cancelInvite() {
        String id = pendingInviteId.getValue();
        if (id == null) return;
        friendsRepository.updateInviteStatus(id, "cancelled");
    }

    public void expireInvite() {
        String id = pendingInviteId.getValue();
        if (id == null) return;
        friendsRepository.updateInviteStatus(id, "expired");
    }

    private void listenToOutgoingInvite(String inviteId) {
        if (outgoingListener != null && outgoingRef != null) outgoingRef.removeEventListener(outgoingListener);

        outgoingRef = rtdb.getReference("invites").child(inviteId);
        outgoingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if ("accepted".equals(status)) {
                    String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    String opponentUid = snapshot.child("toUid").getValue(String.class);
                    String fromUsername = snapshot.child("fromUsername").getValue(String.class);
                    String toUsername = snapshot.child("toUsername").getValue(String.class);

                    matchRepository.createInviteMatch(inviteId, myUid, opponentUid, fromUsername, toUsername,
                            new RepositoryCallback<String>() {
                                @Override public void onSuccess(String matchId) {
                                    pendingInviteUsername.postValue(toUsername);
                                    pendingMatchId.postValue(matchId);
                                    inviteResult.postValue("accepted");
                                    stopOutgoingListener();
                                }
                                @Override public void onFailure(Exception e) {
                                    inviteResult.postValue("accepted");
                                    stopOutgoingListener();
                                }
                            });
                } else if ("declined".equals(status) || "expired".equals(status) || "cancelled".equals(status)) {
                    inviteResult.postValue(status);
                    clearPendingInvite();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        outgoingRef.addValueEventListener(outgoingListener);
    }

    private void stopOutgoingListener() {
        if (outgoingRef != null && outgoingListener != null) outgoingRef.removeEventListener(outgoingListener);
        outgoingListener = null;
        outgoingRef = null;
    }

    public void clearPendingInvite() {
        stopOutgoingListener();
        pendingInviteId.postValue(null);
        pendingInviteUsername.postValue(null);
        pendingMatchId.postValue(null);
    }

    public void clearInviteResult() { inviteResult.setValue(null); }

    public void acceptInvite(String inviteId) {
        friendsRepository.updateInviteStatus(inviteId, "accepted");
    }

    public void declineInvite(String inviteId) {
        friendsRepository.updateInviteStatus(inviteId, "declined");
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        for (Map.Entry<String, ValueEventListener> entry : presenceListeners.entrySet()) {
            rtdb.getReference("presence/" + entry.getKey()).removeEventListener(entry.getValue());
        }
        if (outgoingRef != null && outgoingListener != null) outgoingRef.removeEventListener(outgoingListener);
    }
}