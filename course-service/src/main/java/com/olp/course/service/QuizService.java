package com.olp.course.service;

import com.olp.course.dto.QuizAttemptBreakdownResponse;
import com.olp.course.dto.QuizQuestionResultResponse;
import com.olp.course.model.Course;
import com.olp.course.model.Question;
import com.olp.course.model.Quiz;
import com.olp.course.model.QuizAttempt;
import com.olp.course.model.QuizAttemptAnswer;
import com.olp.course.repository.CourseRepository;
import com.olp.course.repository.QuestionRepository;
import com.olp.course.repository.QuizAttemptRepository;
import com.olp.course.repository.QuizRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final AccessControlService accessControlService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public QuizService(QuizRepository quizRepository, QuestionRepository questionRepository,
                       QuizAttemptRepository attemptRepository, CourseRepository courseRepository,
                       AccessControlService accessControlService,
                       KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.accessControlService = accessControlService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public Quiz createQuiz(Long courseId, Quiz quiz, Long userId, String role) {
        Course course = accessControlService.getOwnedCourse(courseId, userId, role);
        quiz.setCourseId(courseId);
        Quiz saved = quizRepository.save(quiz);
        publishQuizEvent(saved, course);
        return saved;
    }

    public List<Quiz> getQuizzesByCourse(Long courseId) {
        return quizRepository.findByCourseId(courseId);
    }

    public Optional<Quiz> getQuizById(Long id) {
        return quizRepository.findById(id);
    }

    public Optional<Quiz> updateQuiz(Long id, Quiz quizDetails, Long userId, String role) {
        return quizRepository.findById(id).map(quiz -> {
            accessControlService.getOwnedCourse(quiz.getCourseId(), userId, role);
            quiz.setLessonId(quizDetails.getLessonId());
            quiz.setTitle(quizDetails.getTitle());
            quiz.setDescription(quizDetails.getDescription());
            quiz.setPassingScore(quizDetails.getPassingScore());
            quiz.setTimeLimitMinutes(quizDetails.getTimeLimitMinutes());
            quiz.setMaxAttempts(quizDetails.getMaxAttempts());
            quiz.setIsPublished(quizDetails.getIsPublished());
            return quizRepository.save(quiz);
        });
    }

    public boolean deleteQuiz(Long id, Long userId, String role) {
        return quizRepository.findById(id).map(quiz -> {
            accessControlService.getOwnedCourse(quiz.getCourseId(), userId, role);
            quizRepository.delete(quiz);
            return true;
        }).orElse(false);
    }

    public Question addQuestion(Long quizId, Question question, Long userId, String role) {
        Quiz quiz = accessControlService.getOwnedQuiz(quizId, userId, role);
        question.setQuiz(quiz);
        return questionRepository.save(question);
    }

    public Optional<Question> updateQuestion(Long qId, Question questionDetails, Long userId, String role) {
        return questionRepository.findById(qId).map(question -> {
            accessControlService.getOwnedQuestion(qId, userId, role);
            question.setQuestionText(questionDetails.getQuestionText());
            question.setOptionA(questionDetails.getOptionA());
            question.setOptionB(questionDetails.getOptionB());
            question.setOptionC(questionDetails.getOptionC());
            question.setOptionD(questionDetails.getOptionD());
            question.setType(questionDetails.getType());
            question.setCorrectAnswer(questionDetails.getCorrectAnswer());
            question.setPoints(questionDetails.getPoints());
            question.setOrderIndex(questionDetails.getOrderIndex());
            return questionRepository.save(question);
        });
    }

    public boolean deleteQuestion(Long qId, Long userId, String role) {
        return questionRepository.findById(qId).map(question -> {
            accessControlService.getOwnedQuestion(qId, userId, role);
            questionRepository.delete(question);
            return true;
        }).orElse(false);
    }

    public QuizAttempt startAttempt(Long quizId, Long userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
        if (!Boolean.TRUE.equals(quiz.getIsPublished())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz is not published");
        }
        if (quiz.getMaxAttempts() != null && quiz.getMaxAttempts() > 0) {
            long attempts = attemptRepository.countByQuizIdAndUserId(quizId, userId);
            if (attempts >= quiz.getMaxAttempts()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Maximum attempt limit reached");
            }
        }
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setUserId(userId);
        attempt.setStartedAt(LocalDateTime.now());
        
        int maxScore = questionRepository.findByQuizId(quizId).stream()
                .mapToInt(q -> q.getPoints() != null ? q.getPoints() : 1)
                .sum();
        attempt.setMaxScore(maxScore);
        
        return attemptRepository.save(attempt);
    }

    public QuizAttempt submitAttempt(Long attemptId, Map<Long, String> answers) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));
        if (attempt.getSubmittedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attempt has already been submitted");
        }

        List<Question> questions = questionRepository.findByQuizId(attempt.getQuiz().getId());
        int score = 0;
        boolean timedOut = false;
        if (attempt.getQuiz().getTimeLimitMinutes() != null && attempt.getQuiz().getTimeLimitMinutes() > 0 && attempt.getStartedAt() != null) {
            timedOut = attempt.getStartedAt()
                    .plusMinutes(attempt.getQuiz().getTimeLimitMinutes())
                    .isBefore(LocalDateTime.now());
        }

        for (Question question : questions) {
            String selected = answers.get(question.getId());
            QuizAttemptAnswer attemptAnswer = new QuizAttemptAnswer();
            attemptAnswer.setAttempt(attempt);
            attemptAnswer.setQuestionId(question.getId());
            attemptAnswer.setSelectedAnswer(selected);
            
            if (selected != null && isCorrectAnswer(question, selected)) {
                attemptAnswer.setIsCorrect(true);
                score += question.getPoints() != null ? question.getPoints() : 1;
            } else {
                attemptAnswer.setIsCorrect(false);
            }
            attempt.getAnswers().add(attemptAnswer);
        }

        attempt.setScore(score);
        attempt.setIsPassed(score >= attempt.getQuiz().getPassingScore());
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setTimedOut(timedOut);

        QuizAttempt saved = attemptRepository.save(attempt);
        publishQuizResultEvent(saved);
        return saved;
    }

    public List<QuizAttempt> getMyAttempts(Long quizId, Long userId) {
        return attemptRepository.findByQuizIdAndUserId(quizId, userId);
    }

    public Optional<Quiz> publishQuiz(Long quizId, Long userId, String role, boolean published) {
        return quizRepository.findById(quizId).map(quiz -> {
            accessControlService.getOwnedCourse(quiz.getCourseId(), userId, role);
            quiz.setIsPublished(published);
            return quizRepository.save(quiz);
        });
    }

    public QuizAttemptBreakdownResponse getAttemptBreakdown(Long attemptId, Long userId, String role) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));
        boolean owner = userId != null && userId.equals(attempt.getUserId());
        boolean admin = accessControlService.isAdmin(role);
        boolean instructor = accessControlService.isInstructor(role);
        if (instructor) {
            accessControlService.getOwnedCourse(attempt.getQuiz().getCourseId(), userId, role);
        }
        if (!owner && !admin && !instructor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this attempt");
        }

        Map<Long, Question> questionMap = questionRepository.findByQuizId(attempt.getQuiz().getId())
                .stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        QuizAttemptBreakdownResponse response = new QuizAttemptBreakdownResponse();
        response.setAttemptId(attempt.getId());
        response.setQuizId(attempt.getQuiz().getId());
        response.setScore(attempt.getScore());
        response.setMaxScore(attempt.getMaxScore());
        response.setPassed(attempt.getIsPassed());
        response.setTimedOut(attempt.getTimedOut());
        response.setStartedAt(attempt.getStartedAt());
        response.setSubmittedAt(attempt.getSubmittedAt());

        response.setResults(attempt.getAnswers().stream()
                .map(answer -> {
                    Question question = questionMap.get(answer.getQuestionId());
                    QuizQuestionResultResponse questionResult = new QuizQuestionResultResponse();
                    questionResult.setQuestionId(answer.getQuestionId());
                    if (question != null) {
                        questionResult.setQuestionText(question.getQuestionText());
                        questionResult.setQuestionType(question.getType());
                        questionResult.setCorrectAnswer(question.getCorrectAnswer());
                        questionResult.setMaxPoints(question.getPoints());
                        questionResult.setAwardedPoints(Boolean.TRUE.equals(answer.getIsCorrect()) ? question.getPoints() : 0);
                    }
                    questionResult.setSelectedAnswer(answer.getSelectedAnswer());
                    questionResult.setCorrect(answer.getIsCorrect());
                    return questionResult;
                })
                .collect(Collectors.toList()));
        return response;
    }

    private boolean isCorrectAnswer(Question question, String selected) {
        String type = question.getType() == null ? "MCQ_SINGLE" : question.getType().trim().toUpperCase();
        String correctAnswer = question.getCorrectAnswer() == null ? "" : question.getCorrectAnswer();
        if ("MCQ_MULTI".equals(type)) {
            return normalizeAnswers(selected).equals(normalizeAnswers(correctAnswer));
        }
        return correctAnswer.trim().equalsIgnoreCase(selected.trim());
    }

    private String normalizeAnswers(String answers) {
        return java.util.Arrays.stream((answers == null ? "" : answers).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private void publishQuizEvent(Quiz quiz, Course course) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "QUIZ_CREATED");
            event.put("courseId", course.getId());
            event.put("courseTitle", course.getTitle());
            event.put("quizId", quiz.getId());
            event.put("quizTitle", quiz.getTitle());
            kafkaTemplate.send("course-events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            System.err.println("Error publishing quiz event: " + e.getMessage());
        }
    }

    private void publishQuizResultEvent(QuizAttempt attempt) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "QUIZ_RESULT");
            event.put("courseId", attempt.getQuiz().getCourseId());
            event.put("quizId", attempt.getQuiz().getId());
            event.put("quizTitle", attempt.getQuiz().getTitle());
            event.put("attemptId", attempt.getId());
            event.put("userId", attempt.getUserId());
            event.put("score", attempt.getScore());
            event.put("maxScore", attempt.getMaxScore());
            event.put("passed", attempt.getIsPassed());
            event.put("timedOut", attempt.getTimedOut());
            event.put("submittedAt", attempt.getSubmittedAt() != null ? attempt.getSubmittedAt().toString() : null);
            kafkaTemplate.send("course-events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            System.err.println("Error publishing quiz result event: " + e.getMessage());
        }
    }
}
