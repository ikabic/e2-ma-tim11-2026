package com.slagalica.app.model;

import java.util.List;

public class KorakPoKorakQuestion {

    private String id;
    private List<String> answers;
    private List<String> clues;

    public KorakPoKorakQuestion() {}

    public KorakPoKorakQuestion(String id, List<String> answers, List<String> clues) {
        this.id = id;
        this.answers = answers;
        this.clues = clues;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<String> getAnswers() { return answers; }
    public void setAnswers(List<String> answers) { this.answers = answers; }

    public List<String> getClues() { return clues; }
    public void setClues(List<String> clues) { this.clues = clues; }
}
