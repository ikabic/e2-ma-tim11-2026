package com.slagalica.app.model;

import java.util.List;
import java.util.Map;

public class SpojniceQuestion {

    private String id;
    private String text;
    private List<String> leftTerms;
    private List<String> rightTerms;
    private Map<String, String> correctPairs;

    public SpojniceQuestion() {}

    public SpojniceQuestion(String id, String text, List<String> leftTerms, List<String> rightTerms, Map<String, String> correctPairs) {
        this.id = id;
        this.text = text;
        this.leftTerms = leftTerms;
        this.rightTerms = rightTerms;
        this.correctPairs = correctPairs;
    }

    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getLeftTerms() {
        return leftTerms;
    }

    public void setLeftTerms(List<String> leftTerms) {
        this.leftTerms = leftTerms;
    }

    public List<String> getRightTerms() {
        return rightTerms;
    }

    public void setRightTerms(List<String> rightTerms) {
        this.rightTerms = rightTerms;
    }

    public Map<String, String> getCorrectPairs() {
        return correctPairs;
    }

    public void setCorrectPairs(Map<String, String> correctPairs) {
        this.correctPairs = correctPairs;
    }
}
