package com.olp.course.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QuizAttemptBreakdownResponse {

    private Long attemptId;
    private Long quizId;
    private Integer score;
    private Integer maxScore;
    private Boolean passed;
    private Boolean timedOut;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private List<QuizQuestionResultResponse> results = new ArrayList<>();

    public Long getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(Long attemptId) {
        this.attemptId = attemptId;
    }

    public Long getQuizId() {
        return quizId;
    }

    public void setQuizId(Long quizId) {
        this.quizId = quizId;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public Boolean getTimedOut() {
        return timedOut;
    }

    public void setTimedOut(Boolean timedOut) {
        this.timedOut = timedOut;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public List<QuizQuestionResultResponse> getResults() {
        return results;
    }

    public void setResults(List<QuizQuestionResultResponse> results) {
        this.results = results;
    }
}
