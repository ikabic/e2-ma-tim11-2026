package com.slagalica.app.model;

import java.util.List;

public class KoZnaZnaQuestion {

    private String id;
    private String text;
    private List<String> answers;
    private int correctAnswerIndex;

    public KoZnaZnaQuestion() {}

    public KoZnaZnaQuestion(String id, String text, List<String> answers, int correctAnswerIndex) {
        this.id = id;
        this.text = text;
        this.answers = answers;
        this.correctAnswerIndex = correctAnswerIndex;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public void setAnswers(List<String> answers) {
        this.answers = answers;
    }

    public int getCorrectAnswerIndex() {
        return correctAnswerIndex;
    }

    public void setCorrectAnswerIndex(int correctAnswerIndex) {
        this.correctAnswerIndex = correctAnswerIndex;
    }
}
