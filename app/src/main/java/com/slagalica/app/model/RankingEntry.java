package com.slagalica.app.model;

public class RankingEntry {
    private String userId;
    private String username;
    private int cycleStars;
    private int totalStars;
    private int rank;

    public RankingEntry() {}

    public RankingEntry(String userId, String username, int cycleStars, int totalStars) {
        this.userId     = userId;
        this.username   = username;
        this.cycleStars = cycleStars;
        this.totalStars = totalStars;
    }

    public String getUserId() { return userId; }
    public void setUserId(String v) { this.userId = v; }

    public String getUsername() { return username; }
    public void setUsername(String v)  { this.username = v; }

    public int getCycleStars()  { return cycleStars; }
    public void setCycleStars(int v) { this.cycleStars = v; }

    public int getTotalStars() { return totalStars; }
    public void setTotalStars(int v) { this.totalStars = v; }

    public int getRank() { return rank; }
    public void setRank(int v) { this.rank = v; }
}