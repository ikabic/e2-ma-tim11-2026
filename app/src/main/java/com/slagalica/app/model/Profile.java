package com.slagalica.app.model;

import com.google.firebase.firestore.Exclude;

import java.util.List;
import java.util.Objects;

public class Profile {

    private String userId;
    private String avatarUrl;
    private int tokens;
    private int stars;
    private int prevCycleRegionRank;

    public Profile() {}

    public Profile(String userId, String avatarUrl, int tokens, int stars, int prevCycleRegionRank) {
        this.userId = userId;
        this.avatarUrl = avatarUrl;
        this.tokens = tokens;
        this.stars = stars;
        this.prevCycleRegionRank = prevCycleRegionRank;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        this.stars = stars;
    }

    public int getPrevCycleRegionRank() {
        return prevCycleRegionRank;
    }

    public void setPrevCycleRegionRank(int prevCycleRegionRank) {
        this.prevCycleRegionRank = prevCycleRegionRank;
    }

    @Exclude
    public List<String> leagueNames = List.of("Unranked", "Bronze", "Silver", "Gold", "Platinum", "Diamond");

    public String getLeague(String info) {
        int league = 0;
        int required = 100;

        while (stars >= required && league < leagueNames.size() - 1) {
            league++;
            required *= 2;
        }

        if (Objects.equals(info, "points"))
            return Integer.toString(required);
        else if (Objects.equals(info, "next"))
            return league < leagueNames.size() - 1 ? leagueNames.get(league + 1) : "Max";
        else
            return leagueNames.get(league);
    }
}
