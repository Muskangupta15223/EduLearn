package com.olp.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.course.dto.QuizAttemptBreakdownResponse;
import com.olp.course.model.Course;
import com.olp.course.model.Question;
import com.olp.course.model.Quiz;
import com.olp.course.model.QuizAttempt;
import com.olp.course.model.QuizAttemptAnswer;
import com.olp.course.repository.CourseRepository;
import com.olp.course.repository.QuestionRepository;
import com.olp.course.repository.QuizAttemptRepository;
import com.olp.course.repository.QuizRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuizAttemptRepository attemptRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private QuizService quizService;

    @BeforeEach
    void setUp() {
        quizService = new QuizService(
                quizRepository,
                questionRepository,
                attemptRepository,
                courseRepository,
                accessControlService,
                kafkaTemplate,
                new ObjectMapper()
        );
    }

    @Test
    void startAttemptRejectsWhenMaxAttemptsReached() {
        Quiz quiz = new Quiz();
        quiz.setId(5L);
        quiz.setCourseId(10L);
        quiz.setIsPublished(true);
        quiz.setMaxAttempts(2);

        when(quizRepository.findById(5L)).thenReturn(Optional.of(quiz));
        when(attemptRepository.countByQuizIdAndUserId(5L, 3L)).thenReturn(2L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> quizService.startAttempt(5L, 3L));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void getAttemptBreakdownReturnsQuestionLevelResults() {
        Quiz quiz = new Quiz();
        quiz.setId(8L);
        quiz.setCourseId(11L);

        Question question = new Question();
        question.setId(21L);
        question.setQuiz(quiz);
        question.setQuestionText("2 + 2 = ?");
        question.setType("MCQ_SINGLE");
        question.setCorrectAnswer("B");
        question.setPoints(2);

        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(33L);
        attempt.setQuiz(quiz);
        attempt.setUserId(7L);
        attempt.setScore(2);
        attempt.setMaxScore(2);
        attempt.setIsPassed(true);
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(1));
        attempt.setSubmittedAt(LocalDateTime.now());

        QuizAttemptAnswer answer = new QuizAttemptAnswer();
        answer.setAttempt(attempt);
        answer.setQuestionId(21L);
        answer.setSelectedAnswer("B");
        answer.setIsCorrect(true);
        attempt.setAnswers(List.of(answer));

        when(attemptRepository.findById(33L)).thenReturn(Optional.of(attempt));
        when(accessControlService.isAdmin("STUDENT")).thenReturn(false);
        when(accessControlService.isInstructor("STUDENT")).thenReturn(false);
        when(questionRepository.findByQuizId(8L)).thenReturn(List.of(question));

        QuizAttemptBreakdownResponse response = quizService.getAttemptBreakdown(33L, 7L, "STUDENT");

        assertEquals(1, response.getResults().size());
        assertEquals("B", response.getResults().get(0).getSelectedAnswer());
        assertEquals(true, response.getResults().get(0).getCorrect());
    }

    @Test
    void submitAttemptPublishesQuizResultEvent() {
        Quiz quiz = new Quiz();
        quiz.setId(9L);
        quiz.setCourseId(12L);
        quiz.setTitle("Java Basics");
        quiz.setPassingScore(1);

        Question question = new Question();
        question.setId(100L);
        question.setQuiz(quiz);
        question.setCorrectAnswer("A");
        question.setPoints(2);
        question.setType("MCQ_SINGLE");

        QuizAttempt attempt = new QuizAttempt();
        attempt.setId(50L);
        attempt.setQuiz(quiz);
        attempt.setUserId(6L);
        attempt.setStartedAt(LocalDateTime.now().minusMinutes(1));

        Course course = new Course();
        course.setId(12L);
        course.setTitle("Java Basics");
        course.setInstructorId(99L);

        when(attemptRepository.findById(50L)).thenReturn(Optional.of(attempt));
        when(questionRepository.findByQuizId(9L)).thenReturn(List.of(question));
        when(courseRepository.findById(12L)).thenReturn(Optional.of(course));
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuizAttempt saved = quizService.submitAttempt(50L, new HashMap<>(java.util.Map.of(100L, "A")));

        assertEquals(2, saved.getScore());
        verify(kafkaTemplate).send(eq("course-events"), contains("\"eventType\":\"QUIZ_RESULT\""));
        verify(kafkaTemplate).send(eq("course-events"), contains("\"instructorId\":99"));
    }

    @Test
    void createQuizPublishesQuizAvailabilityEventWhenQuizIsPublished() {
        Course course = new Course();
        course.setId(12L);
        course.setTitle("Java Basics");

        Quiz quiz = new Quiz();
        quiz.setTitle("Variables");
        quiz.setIsPublished(true);

        Quiz saved = new Quiz();
        saved.setId(91L);
        saved.setCourseId(12L);
        saved.setTitle("Variables");
        saved.setIsPublished(true);

        when(accessControlService.getOwnedCourse(12L, 6L, "INSTRUCTOR")).thenReturn(course);
        when(quizRepository.save(any(Quiz.class))).thenReturn(saved);

        Quiz result = quizService.createQuiz(12L, quiz, 6L, "INSTRUCTOR");

        assertEquals(91L, result.getId());
        verify(kafkaTemplate).send(eq("course-events"), contains("\"eventType\":\"QUIZ_PUBLISHED\""));
        verify(kafkaTemplate).send(eq("course-events"), contains("\"courseTitle\":\"Java Basics\""));
    }

    @Test
    void createQuizDoesNotNotifyStudentsWhenQuizIsDraft() {
        Course course = new Course();
        course.setId(12L);
        course.setTitle("Java Basics");

        Quiz quiz = new Quiz();
        quiz.setTitle("Variables");
        quiz.setIsPublished(false);

        Quiz saved = new Quiz();
        saved.setId(91L);
        saved.setCourseId(12L);
        saved.setTitle("Variables");
        saved.setIsPublished(false);

        when(accessControlService.getOwnedCourse(12L, 6L, "INSTRUCTOR")).thenReturn(course);
        when(quizRepository.save(any(Quiz.class))).thenReturn(saved);

        quizService.createQuiz(12L, quiz, 6L, "INSTRUCTOR");

        verify(kafkaTemplate, never()).send(eq("course-events"), contains("\"eventType\":\"QUIZ_PUBLISHED\""));
    }
}
