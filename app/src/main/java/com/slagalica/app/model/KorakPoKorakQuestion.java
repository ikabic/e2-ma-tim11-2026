package com.slagalica.app.model;

import java.util.List;

public class KorakPoKorakQuestion {

    private String id;
    private String answer;
    private List<String> clues;

    public KorakPoKorakQuestion() {}

    public KorakPoKorakQuestion(String id, String answer, List<String> clues) {
        this.id = id;
        this.answer = answer;
        this.clues = clues;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<String> getClues() { return clues; }
    public void setClues(List<String> clues) { this.clues = clues; }
}
