package com.slagalica.app.model;

import com.google.firebase.database.Exclude;

public class Challenge {

    private String id;
    private String creatorUid;
    private String creatorUsername;
    private String region;
    private int starsStake;
    private int tokensStake;
    private String status;
    private long createdAt;
    private int playerCount;

    public Challenge() {}

    public Challenge(String id, String creatorUid, String creatorUsername, String region,
                     int starsStake, int tokensStake, String status, long createdAt) {
        this.id = id;
        this.creatorUid = creatorUid;
        this.creatorUsername = creatorUsername;
        this.region = region;
        this.starsStake = starsStake;
        this.tokensStake = tokensStake;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCreatorUid() { return creatorUid; }
    public void setCreatorUid(String creatorUid) { this.creatorUid = creatorUid; }

    public String getCreatorUsername() { return creatorUsername; }
    public void setCreatorUsername(String creatorUsername) { this.creatorUsername = creatorUsername; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public int getStarsStake() { return starsStake; }
    public void setStarsStake(int starsStake) { this.starsStake = starsStake; }

    public int getTokensStake() { return tokensStake; }
    public void setTokensStake(int tokensStake) { this.tokensStake = tokensStake; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    @Exclude
    public int getPlayerCount() { return playerCount; }
    @Exclude
    public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }
}
