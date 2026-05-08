package com.slagalica.app.model;

public class KoZnaZnaAnswer {
    private String questionId;
    private int answerIndex;
    private long timestamp;

    public KoZnaZnaAnswer() {}

    public KoZnaZnaAnswer(String questionId, int answerIndex, long timestamp) {
        this.questionId = questionId;
        this.answerIndex = answerIndex;
        this.timestamp = timestamp;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public int getAnswerIndex() {
        return answerIndex;
    }

    public void setAnswerIndex(int answerIndex) {
        this.answerIndex = answerIndex;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
