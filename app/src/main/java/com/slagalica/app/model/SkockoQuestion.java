package com.slagalica.app.model;

import java.util.List;
public class SkockoQuestion {

    private String id;
    private List<Integer> solution;

    public SkockoQuestion() {}

    public SkockoQuestion(String id, List<Integer> solution) {
        this.id = id;
        this.solution = solution;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<Integer> getSolution() { return solution; }
    public void setSolution(List<Integer> solution) { this.solution = solution; }
}