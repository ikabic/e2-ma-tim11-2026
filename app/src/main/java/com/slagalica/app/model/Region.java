package com.slagalica.app.model;

import androidx.annotation.NonNull;

import java.util.List;

public class Region {

    private String regionKey;
    private String displayName;
    private List<Double> bounds;
    private int icon;
    private double centreLat;
    private double centreLng;
    private int cycleStars;
    private int activePlayers;
    private int totalPlayers;
    private int goldCount;
    private int silverCount;
    private int bronzeCount;
    private int rank;

    public Region() {}

    public Region(String regionKey, String displayName, int icon, double centreLat, double centreLng) {
        this.regionKey = regionKey;
        this.displayName = displayName;
        this.icon = icon;
        this.centreLat = centreLat;
        this.centreLng = centreLng;
    }

    public String getRegionKey() {
        return regionKey;
    }

    public void setRegionKey(String regionKey) {
        this.regionKey = regionKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getIcon() {
        return icon;
    }

    public void setIcon(int icon) {
        this.icon = icon;
    }

    public double getCentreLat() {
        return centreLat;
    }

    public void setCentreLat(double centreLat) {
        this.centreLat = centreLat;
    }

    public double getCentreLng() {
        return centreLng;
    }

    public void setCentreLng(double centreLng) {
        this.centreLng = centreLng;
    }

    public int getCycleStars() {
        return cycleStars;
    }

    public void setCycleStars(int cycleStars) {
        this.cycleStars = cycleStars;
    }

    public int getActivePlayers() {
        return activePlayers;
    }

    public void setActivePlayers(int activePlayers) {
        this.activePlayers = activePlayers;
    }

    public int getTotalPlayers() {
        return totalPlayers;
    }

    public void setTotalPlayers(int totalPlayers) {
        this.totalPlayers = totalPlayers;
    }

    public int getGoldCount() {
        return goldCount;
    }

    public void setGoldCount(int goldCount) {
        this.goldCount = goldCount;
    }

    public int getSilverCount() {
        return silverCount;
    }

    public void setSilverCount(int silverCount) {
        this.silverCount = silverCount;
    }

    public int getBronzeCount() {
        return bronzeCount;
    }

    public void setBronzeCount(int bronzeCount) {
        this.bronzeCount = bronzeCount;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public List<Double> getBounds() {
        return bounds;
    }

    public void setBounds(List<Double> bounds) {
        this.bounds = bounds;
    }
}