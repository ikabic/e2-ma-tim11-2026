package com.slagalica.app.model;

import java.util.HashMap;
import java.util.Map;

public class GameStatistics {
    private int gameId;
    private int totalPlayed;
    private int totalPoints;
    private int minPoints = 0;
    private int maxPoints = 20;
    private int guessesMade = 0;
    private int correctGuesses = 0;
    private int wrongGuesses = 0;

    private Map<String, Long> dynamicFields = new HashMap<>();

    public void setDynamicFields(Map<String, Long> dynamicFields) {
        this.dynamicFields = dynamicFields;
    }

    public long getMetric(String key) {
        if (dynamicFields != null && dynamicFields.containsKey(key)) {
            Long value = dynamicFields.get(key);
            return value != null ? value : 0L;
        }

        switch (key) {
            case "correctGuesses": return correctGuesses;
            case "wrongGuesses": return wrongGuesses;
            case "guessesMade": return guessesMade;
            default: return 0L;
        }
    }

    public GameStatistics(int gameId, int totalPlayed, int totalPoints, int maxPoints, int minPoints) {
        this.gameId = gameId;
        this.totalPlayed = totalPlayed;
        this.totalPoints = totalPoints;
        this.maxPoints = maxPoints;
        this.minPoints = minPoints;
    }

    public int getGameId() {
        return gameId;
    }

    public int getTotalPlayed() {
        return totalPlayed;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public int getMinPoints() {
        return minPoints;
    }

    public int getMaxPoints() {
        return maxPoints;
    }
}
