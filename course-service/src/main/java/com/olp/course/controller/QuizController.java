package com.olp.course.controller;

import com.olp.course.dto.QuizAttemptBreakdownResponse;
import com.olp.course.model.Question;
import com.olp.course.model.Quiz;
import com.olp.course.model.QuizAttempt;
import com.olp.course.service.QuizService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    // ── Quiz CRUD ──

    @PostMapping("/courses/{courseId}/quizzes")
    public ResponseEntity<Quiz> createQuiz(
            @PathVariable Long courseId,
            @RequestBody Quiz quiz,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return ResponseEntity.ok(quizService.createQuiz(courseId, quiz, userId, role));
    }

    @GetMapping("/courses/{courseId}/quizzes")
    public ResponseEntity<List<Quiz>> getQuizzesByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(quizService.getQuizzesByCourse(courseId));
    }

    @GetMapping("/quizzes/{id}")
    public ResponseEntity<Quiz> getQuizById(@PathVariable Long id) {
        return quizService.getQuizById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/quizzes/{id}")
    public ResponseEntity<Quiz> updateQuiz(
            @PathVariable Long id,
            @RequestBody Quiz quiz,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return quizService.updateQuiz(id, quiz, userId, role)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/quizzes/{id}")
    public ResponseEntity<?> deleteQuiz(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        if (quizService.deleteQuiz(id, userId, role)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ── Question CRUD ──

    @PostMapping("/quizzes/{quizId}/questions")
    public ResponseEntity<Question> addQuestion(
            @PathVariable Long quizId,
            @RequestBody Question question,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return ResponseEntity.ok(quizService.addQuestion(quizId, question, userId, role));
    }

    @PutMapping("/questions/{qId}")
    public ResponseEntity<Question> updateQuestion(
            @PathVariable Long qId,
            @RequestBody Question question,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return quizService.updateQuestion(qId, question, userId, role)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/questions/{qId}")
    public ResponseEntity<?> deleteQuestion(
            @PathVariable Long qId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        if (quizService.deleteQuestion(qId, userId, role)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ── Attempts ──

    @PostMapping("/quizzes/{quizId}/attempts")
    public ResponseEntity<QuizAttempt> startAttempt(@PathVariable Long quizId,
                                                     @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(quizService.startAttempt(quizId, userId));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<QuizAttempt> submitAttempt(@PathVariable Long attemptId,
                                                      @RequestBody Map<String, Object> body) {
        Map<Long, String> answers = new java.util.HashMap<>();
        Object answersObj = body.get("answers");
        if (answersObj instanceof Map) {
            ((Map<?, ?>) answersObj).forEach((k, v) -> answers.put(Long.valueOf(k.toString()), v.toString()));
        }
        return ResponseEntity.ok(quizService.submitAttempt(attemptId, answers));
    }

    @GetMapping("/quizzes/{quizId}/attempts/me")
    public ResponseEntity<List<QuizAttempt>> getMyAttempts(@PathVariable Long quizId,
                                                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(quizService.getMyAttempts(quizId, userId));
    }

    @GetMapping("/quizzes/{quizId}/best-score/me")
    public ResponseEntity<Map<String, Object>> getBestScore(@PathVariable Long quizId,
                                                             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        List<QuizAttempt> attempts = quizService.getMyAttempts(quizId, userId);
        if (attempts.isEmpty()) {
            return ResponseEntity.ok(Map.of("bestScore", 0, "attempts", 0));
        }
        int best = attempts.stream().map(QuizAttempt::getScore).max(Comparator.naturalOrder()).orElse(0);
        return ResponseEntity.ok(Map.of("bestScore", best, "attempts", attempts.size()));
    }

    @PutMapping("/quizzes/{id}/publish")
    public ResponseEntity<Quiz> publishQuiz(@PathVariable Long id,
                                            @RequestParam(defaultValue = "true") boolean published,
                                            @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return quizService.publishQuiz(id, userId, role, published)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/attempts/{attemptId}/breakdown")
    public ResponseEntity<QuizAttemptBreakdownResponse> getAttemptBreakdown(
            @PathVariable Long attemptId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(quizService.getAttemptBreakdown(attemptId, userId, role));
    }
}
