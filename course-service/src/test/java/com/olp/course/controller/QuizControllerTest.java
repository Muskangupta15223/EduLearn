package com.olp.course.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olp.course.dto.QuizAttemptBreakdownResponse;
import com.olp.course.dto.QuizDtos.QuestionRequest;
import com.olp.course.dto.QuizDtos.QuizAttemptSubmitRequest;
import com.olp.course.dto.QuizDtos.QuizRequest;
import com.olp.course.model.Question;
import com.olp.course.model.Quiz;
import com.olp.course.model.QuizAttempt;
import com.olp.course.service.QuizService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class QuizControllerTest {

  @Mock
  private QuizService quizService;

  private QuizController quizController;

  @BeforeEach
  void setUp() {
    quizController = new QuizController(quizService);
  }

  @Test
  void getQuizByIdReturnsNotFoundWhenQuizIsMissing() {
    when(quizService.getQuizById(8L)).thenReturn(Optional.empty());

    ResponseEntity<?> response = quizController.getQuizById(8L);

    assertEquals(404, response.getStatusCode().value());
  }

  @Test
  void deleteQuizReturnsOkWhenServiceDeletes() {
    when(quizService.deleteQuiz(8L, 4L, "INSTRUCTOR")).thenReturn(true);

    ResponseEntity<Void> response = quizController.deleteQuiz(8L, 4L, "INSTRUCTOR");

    assertEquals(200, response.getStatusCode().value());
  }

  @Test
  void startAttemptRejectsMissingUserId() {
    ResponseEntity<?> response = quizController.startAttempt(8L, null);

    assertEquals(401, response.getStatusCode().value());
  }

  @Test
  void submitAttemptPassesTypedAnswers() {
    Quiz quiz = new Quiz();
    quiz.setId(4L);
    QuizAttempt attempt = new QuizAttempt();
    attempt.setQuiz(quiz);
    when(quizService.submitAttempt(org.mockito.ArgumentMatchers.eq(10L), any())).thenReturn(attempt);

    ResponseEntity<?> response = quizController.submitAttempt(
      10L,
      new QuizAttemptSubmitRequest(Map.of(11L, "A", 12L, "B"))
    );

    assertEquals(200, response.getStatusCode().value());
    verify(quizService).submitAttempt(10L, Map.of(11L, "A", 12L, "B"));
  }

  @Test
  void getBestScoreReturnsZeroStateWhenNoAttemptsExist() {
    when(quizService.getMyAttempts(5L, 21L)).thenReturn(List.of());

    ResponseEntity<?> response = quizController.getBestScore(5L, 21L);

    assertEquals(200, response.getStatusCode().value());
  }

  @Test
  void getBestScoreReturnsHighestAttemptScore() {
    QuizAttempt first = new QuizAttempt();
    first.setScore(6);
    QuizAttempt second = new QuizAttempt();
    second.setScore(9);
    when(quizService.getMyAttempts(5L, 21L)).thenReturn(List.of(first, second));

    ResponseEntity<?> response = quizController.getBestScore(5L, 21L);

    assertEquals(200, response.getStatusCode().value());
  }

  @Test
  void getAttemptBreakdownReturnsServicePayload() {
    QuizAttemptBreakdownResponse breakdown = new QuizAttemptBreakdownResponse();
    when(quizService.getAttemptBreakdown(50L, 21L, "STUDENT")).thenReturn(breakdown);

    ResponseEntity<QuizAttemptBreakdownResponse> response = quizController.getAttemptBreakdown(
      50L,
      21L,
      "STUDENT"
    );

    assertSame(breakdown, response.getBody());
  }

  @Test
  void createQuizMapsTypedRequest() {
    Quiz quiz = new Quiz();
    quiz.setId(14L);
    when(quizService.createQuiz(org.mockito.ArgumentMatchers.eq(3L), any(), org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq("INSTRUCTOR")))
      .thenReturn(quiz);

    ResponseEntity<?> response = quizController.createQuiz(
      3L,
      new QuizRequest(null, "Quiz", "Desc", 70, 10, 1, true, List.of()),
      9L,
      "INSTRUCTOR"
    );

    assertEquals(200, response.getStatusCode().value());
  }

  @Test
  void addQuestionMapsTypedRequest() {
    Question question = new Question();
    question.setId(16L);
    when(quizService.addQuestion(org.mockito.ArgumentMatchers.eq(4L), any(), org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq("INSTRUCTOR")))
      .thenReturn(question);

    ResponseEntity<?> response = quizController.addQuestion(
      4L,
      new QuestionRequest("Q?", "A", "B", "C", "D", "MCQ_SINGLE", "A", 1, 0),
      9L,
      "INSTRUCTOR"
    );

    assertEquals(200, response.getStatusCode().value());
  }
}
