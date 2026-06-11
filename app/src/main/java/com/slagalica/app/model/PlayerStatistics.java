package com.slagalica.app.model;

import android.util.Pair;

import java.util.List;

public class PlayerStatistics {
    private int matchesPlayed;
    private Pair<Integer, Integer> matchOutcomes; // <won, lost>
    private List<GameStatistics> games;

    public PlayerStatistics(int matchesPlayed, Pair<Integer, Integer> matchOutcomes, List<GameStatistics> games) {
        this.matchesPlayed = matchesPlayed;
        this.matchOutcomes = matchOutcomes;
        this.games = games;
    }

    public int getMatchesPlayed() {
        return matchesPlayed;
    }

    public Pair<Integer, Integer> getMatchOutcomes() {
        return matchOutcomes;
    }

    public List<GameStatistics> getGames() {
        return games;
    }
}
