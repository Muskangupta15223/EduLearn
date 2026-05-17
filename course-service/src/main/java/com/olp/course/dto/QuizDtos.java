package com.olp.course.dto;

import com.olp.course.model.Question;
import com.olp.course.model.Quiz;
import com.olp.course.model.QuizAttempt;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class QuizDtos {
    private QuizDtos() {
    }

    public record QuizRequest(
            Long lessonId,
            String title,
            String description,
            Integer passingScore,
            Integer timeLimitMinutes,
            Integer maxAttempts,
            Boolean isPublished,
            List<QuestionRequest> questions
    ) {
        public Quiz toEntity() {
            Quiz quiz = new Quiz();
            quiz.setLessonId(lessonId);
            quiz.setTitle(title);
            quiz.setDescription(description);
            quiz.setPassingScore(passingScore);
            quiz.setTimeLimitMinutes(timeLimitMinutes);
            quiz.setMaxAttempts(maxAttempts);
            quiz.setIsPublished(isPublished);
            if (questions != null) {
                quiz.setQuestions(questions.stream().map(question -> question.toEntity(quiz)).toList());
            }
            return quiz;
        }
    }

    public record QuizResponse(
            Long id,
            Long courseId,
            Long lessonId,
            String title,
            String description,
            Integer passingScore,
            Integer timeLimitMinutes,
            Integer maxAttempts,
            Boolean isPublished,
            List<QuestionResponse> questions
    ) {
        public static QuizResponse from(Quiz quiz) {
            return new QuizResponse(
                    quiz.getId(),
                    quiz.getCourseId(),
                    quiz.getLessonId(),
                    quiz.getTitle(),
                    quiz.getDescription(),
                    quiz.getPassingScore(),
                    quiz.getTimeLimitMinutes(),
                    quiz.getMaxAttempts(),
                    quiz.getIsPublished(),
                    quiz.getQuestions() == null ? List.of() : quiz.getQuestions().stream().map(QuestionResponse::from).toList()
            );
        }
    }

    public record QuestionRequest(
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String type,
            String correctAnswer,
            Integer points,
            Integer orderIndex
    ) {
        public Question toEntity(Quiz quiz) {
            Question question = new Question();
            question.setQuiz(quiz);
            question.setQuestionText(questionText);
            question.setOptionA(optionA);
            question.setOptionB(optionB);
            question.setOptionC(optionC);
            question.setOptionD(optionD);
            question.setType(type);
            question.setCorrectAnswer(correctAnswer);
            question.setPoints(points);
            question.setOrderIndex(orderIndex);
            return question;
        }
    }

    public record QuestionResponse(
            Long id,
            Long quizId,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String type,
            String correctAnswer,
            Integer points,
            Integer orderIndex
    ) {
        public static QuestionResponse from(Question question) {
            Long quizId = question.getQuiz() == null ? null : question.getQuiz().getId();
            return new QuestionResponse(
                    question.getId(),
                    quizId,
                    question.getQuestionText(),
                    question.getOptionA(),
                    question.getOptionB(),
                    question.getOptionC(),
                    question.getOptionD(),
                    question.getType(),
                    question.getCorrectAnswer(),
                    question.getPoints(),
                    question.getOrderIndex()
            );
        }
    }

    public record QuizAttemptResponse(
            Long id,
            Long userId,
            Long quizId,
            Integer score,
            Integer maxScore,
            Boolean isPassed,
            LocalDateTime attemptedAt,
            LocalDateTime startedAt,
            LocalDateTime submittedAt,
            Boolean timedOut
    ) {
        public static QuizAttemptResponse from(QuizAttempt attempt) {
            Long quizId = attempt.getQuiz() == null ? null : attempt.getQuiz().getId();
            return new QuizAttemptResponse(
                    attempt.getId(),
                    attempt.getUserId(),
                    quizId,
                    attempt.getScore(),
                    attempt.getMaxScore(),
                    attempt.getIsPassed(),
                    attempt.getAttemptedAt(),
                    attempt.getStartedAt(),
                    attempt.getSubmittedAt(),
                    attempt.getTimedOut()
            );
        }
    }

    public record QuizAttemptSubmitRequest(Map<Long, String> answers) {
        public Map<Long, String> normalizedAnswers() {
            return answers == null ? Map.of() : answers;
        }
    }

    public record BestScoreResponse(int bestScore, int attempts) {
    }

    public static List<QuizResponse> toQuizResponses(List<Quiz> quizzes) {
        return quizzes.stream().map(QuizResponse::from).toList();
    }

    public static List<QuizAttemptResponse> toAttemptResponses(List<QuizAttempt> attempts) {
        return attempts.stream().map(QuizAttemptResponse::from).toList();
    }
}
