package com.slagalica.app.model;

import com.google.firebase.firestore.PropertyName;

import java.util.HashMap;
import java.util.Map;

public class DailyMissions {
    private boolean winMatch;
    private boolean sendChat;
    private boolean playFriendly;
    private boolean winTournament;
    private int claimedCount;

    public DailyMissions() {}

    public boolean isWinMatch() { return winMatch; }
    public void setWinMatch(boolean v) { winMatch = v; }
    public boolean isSendChat() { return sendChat; }
    public void setSendChat(boolean v) { sendChat = v; }
    public boolean isPlayFriendly() { return playFriendly; }
    public void setPlayFriendly(boolean v) { playFriendly = v; }
    public boolean isWinTournament() { return winTournament; }
    public void setWinTournament(boolean v) { winTournament = v; }
    public int getClaimedCount() { return claimedCount; }
    public void setClaimedCount(int v) { claimedCount = v; }

    public int completedCount() {
        int c = 0;
        if (winMatch) c++;
        if (sendChat) c++;
        if (playFriendly) c++;
        if (winTournament) c++;
        return c;
    }

    public boolean allCompleted() { return completedCount() == 4; }
    public int unclaimedCount() { return completedCount() - claimedCount; }
    public boolean hasUnclaimedRewards() { return unclaimedCount() > 0; }
    public boolean allClaimed() { return claimedCount >= completedCount() && completedCount() > 0; }

    public int totalStarsAvailable() {
        int stars = unclaimedCount() * 3;
        if (allCompleted() && claimedCount < 4) stars += 3; // bonus
        return stars;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("winMatch", winMatch);
        map.put("sendChat", sendChat);
        map.put("playFriendly", playFriendly);
        map.put("winTournament", winTournament);
        map.put("claimedCount", claimedCount);
        return map;
    }
}
