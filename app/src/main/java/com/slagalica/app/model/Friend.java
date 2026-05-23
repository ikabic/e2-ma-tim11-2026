package com.slagalica.app.model;

public class Friend {

    private String uid;
    private String username;
    private String avatarUrl;
    private int stars;
    private boolean online;
    private boolean inGame;
    private int monthlyRank; // 0 = unranked / unknown

    public Friend() {}

    public Friend(String uid, String username, String avatarUrl, int stars, boolean online, boolean inGame, int monthlyRank) {
        this.uid = uid;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.stars = stars;
        this.online = online;
        this.inGame = inGame;
        this.monthlyRank = monthlyRank;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean isInGame() {
        return inGame;
    }

    public void setInGame(boolean inGame) {
        this.inGame = inGame;
    }

    public int getMonthlyRank() {
        return monthlyRank;
    }

    public void setMonthlyRank(int monthlyRank) {
        this.monthlyRank = monthlyRank;
    }

    public String getLeagueName() {
        String[] names = {"Unranked", "Bronze", "Silver", "Gold", "Platinum", "Diamond"};
        int league = 0;
        int required = 100;
        while (stars >= required && league < names.length - 1) {
            league++;
            required *= 2;
        }
        return names[league];
    }

    public Friend withStatus(String status, Boolean inGame) {
        Friend copy = new Friend(this.uid, this.username, this.avatarUrl, this.stars, this.online, this.inGame, this.monthlyRank);
        copy.online = "online".equals(status) || "in_game".equals(status);
        copy.inGame = inGame != null && inGame;
        return copy;
    }

    public int getStatusPriority() {
        if (isInGame()) return 0;
        if (isOnline()) return 1;
        return 2; // offline
    }
}
