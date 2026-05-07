package com.slagalica.app.model;

import java.util.List;

public class AsocijacijeQuestion {

    private String id;

    private List<String> columnA;
    private List<String> columnB;
    private List<String> columnC;
    private List<String> columnD;

    private List<String> columnAnswers;
    private String finalAnswer;

    public AsocijacijeQuestion() {}

    public AsocijacijeQuestion(
            String id,
            List<String> columnA,
            List<String> columnB,
            List<String> columnC,
            List<String> columnD,
            List<String> columnAnswers,
            String finalAnswer
    ) {
        this.id = id;
        this.columnA = columnA;
        this.columnB = columnB;
        this.columnC = columnC;
        this.columnD = columnD;
        this.columnAnswers = columnAnswers;
        this.finalAnswer = finalAnswer;
    }

    public List<String> getColumnA() { return columnA; }
    public void setColumnA(List<String> columnA) { this.columnA = columnA; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<String> getColumnB() { return columnB; }
    public void setColumnB(List<String> columnB) { this.columnB = columnB; }

    public List<String> getColumnC() { return columnC; }
    public void setColumnC(List<String> columnC) { this.columnC = columnC; }

    public List<String> getColumnD() { return columnD; }
    public void setColumnD(List<String> columnD) { this.columnD = columnD; }

    public List<String> getColumnAnswers() { return columnAnswers; }
    public void setColumnAnswers(List<String> columnAnswers) { this.columnAnswers = columnAnswers; }

    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
}