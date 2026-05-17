package com.olp.course.controller;

import com.olp.course.dto.QuizAttemptBreakdownResponse;
import com.olp.course.dto.QuizDtos;
import com.olp.course.dto.QuizDtos.BestScoreResponse;
import com.olp.course.dto.QuizDtos.QuestionRequest;
import com.olp.course.dto.QuizDtos.QuestionResponse;
import com.olp.course.dto.QuizDtos.QuizAttemptResponse;
import com.olp.course.dto.QuizDtos.QuizAttemptSubmitRequest;
import com.olp.course.dto.QuizDtos.QuizRequest;
import com.olp.course.dto.QuizDtos.QuizResponse;
import com.olp.course.service.QuizService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/courses/{courseId}/quizzes")
    public ResponseEntity<QuizResponse> createQuiz(
            @PathVariable Long courseId,
            @RequestBody QuizRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return ResponseEntity.ok(QuizResponse.from(quizService.createQuiz(courseId, request.toEntity(), userId, role)));
    }

    @GetMapping("/courses/{courseId}/quizzes")
    public ResponseEntity<List<QuizResponse>> getQuizzesByCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(QuizDtos.toQuizResponses(quizService.getQuizzesByCourse(courseId)));
    }

    @GetMapping("/quizzes/{id}")
    public ResponseEntity<QuizResponse> getQuizById(@PathVariable Long id) {
        return quizService.getQuizById(id)
                .map(QuizResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/quizzes/{id}")
    public ResponseEntity<QuizResponse> updateQuiz(
            @PathVariable Long id,
            @RequestBody QuizRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return quizService.updateQuiz(id, request.toEntity(), userId, role)
                .map(QuizResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/quizzes/{id}")
    public ResponseEntity<Void> deleteQuiz(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        if (quizService.deleteQuiz(id, userId, role)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/quizzes/{quizId}/questions")
    public ResponseEntity<QuestionResponse> addQuestion(
            @PathVariable Long quizId,
            @RequestBody QuestionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return ResponseEntity.ok(QuestionResponse.from(quizService.addQuestion(quizId, request.toEntity(null), userId, role)));
    }

    @PutMapping("/questions/{qId}")
    public ResponseEntity<QuestionResponse> updateQuestion(
            @PathVariable Long qId,
            @RequestBody QuestionRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return quizService.updateQuestion(qId, request.toEntity(null), userId, role)
                .map(QuestionResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/questions/{qId}")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable Long qId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        if (quizService.deleteQuestion(qId, userId, role)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/quizzes/{quizId}/attempts")
    public ResponseEntity<QuizAttemptResponse> startAttempt(
            @PathVariable Long quizId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(QuizAttemptResponse.from(quizService.startAttempt(quizId, userId)));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<QuizAttemptResponse> submitAttempt(
            @PathVariable Long attemptId,
            @RequestBody QuizAttemptSubmitRequest request
    ) {
        return ResponseEntity.ok(QuizAttemptResponse.from(quizService.submitAttempt(attemptId, request.normalizedAnswers())));
    }

    @GetMapping("/quizzes/{quizId}/attempts/me")
    public ResponseEntity<List<QuizAttemptResponse>> getMyAttempts(
            @PathVariable Long quizId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(QuizDtos.toAttemptResponses(quizService.getMyAttempts(quizId, userId)));
    }

    @GetMapping("/quizzes/{quizId}/best-score/me")
    public ResponseEntity<BestScoreResponse> getBestScore(
            @PathVariable Long quizId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        List<com.olp.course.model.QuizAttempt> attempts = quizService.getMyAttempts(quizId, userId);
        if (attempts.isEmpty()) {
            return ResponseEntity.ok(new BestScoreResponse(0, 0));
        }
        int best = attempts.stream().map(com.olp.course.model.QuizAttempt::getScore).max(Comparator.naturalOrder()).orElse(0);
        return ResponseEntity.ok(new BestScoreResponse(best, attempts.size()));
    }

    @PutMapping("/quizzes/{id}/publish")
    public ResponseEntity<QuizResponse> publishQuiz(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean published,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return quizService.publishQuiz(id, userId, role, published)
                .map(QuizResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/attempts/{attemptId}/breakdown")
    public ResponseEntity<QuizAttemptBreakdownResponse> getAttemptBreakdown(
            @PathVariable Long attemptId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return ResponseEntity.ok(quizService.getAttemptBreakdown(attemptId, userId, role));
    }
}
