package com.slagalica.app.model;

import com.google.firebase.Timestamp;

public class RankingCycle {
    private String id;
    private String type;
    private Timestamp startDate;
    private Timestamp endDate;
    private boolean rewardsDistributed;

    public RankingCycle() {}

    public String  getId()  { return id; }
    public void setId(String v)  { this.id = v; }
    public String getType()  { return type; }
    public void setType(String v) { this.type = v; }
    public Timestamp getStartDate() { return startDate; }
    public void setStartDate(Timestamp v)  { this.startDate = v; }
    public Timestamp getEndDate() { return endDate; }
    public void setEndDate(Timestamp v) { this.endDate = v; }
    public boolean isRewardsDistributed() { return rewardsDistributed; }
    public void setRewardsDistributed(boolean v) { this.rewardsDistributed = v; }
}