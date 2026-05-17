package com.olp.course.service;

import com.olp.course.model.Assignment;
import com.olp.course.model.Course;
import com.olp.course.model.Lesson;
import com.olp.course.model.Module;
import com.olp.course.model.Question;
import com.olp.course.model.Quiz;
import com.olp.course.repository.AssignmentRepository;
import com.olp.course.repository.CourseRepository;
import com.olp.course.repository.LessonRepository;
import com.olp.course.repository.ModuleRepository;
import com.olp.course.repository.QuestionRepository;
import com.olp.course.repository.QuizRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        accessControlService = new AccessControlService(
                courseRepository,
                moduleRepository,
                lessonRepository,
                quizRepository,
                questionRepository,
                assignmentRepository
        );
    }

    @Test
    void recognizesAdminAndInstructorRolesCaseInsensitively() {
        assertTrue(accessControlService.isAdmin("admin"));
        assertTrue(accessControlService.isInstructor("instructor"));
        assertFalse(accessControlService.isAdmin("student"));
        assertFalse(accessControlService.isInstructor("student"));
    }

    @Test
    void requireInstructorOrAdminRejectsStudents() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> accessControlService.requireInstructorOrAdmin("STUDENT")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getOwnedCourseReturnsCourseForOwningInstructor() {
        Course course = new Course();
        course.setId(10L);
        course.setInstructorId(7L);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        Course owned = accessControlService.getOwnedCourse(10L, 7L, "INSTRUCTOR");

        assertSame(course, owned);
    }

    @Test
    void getOwnedModuleValidatesUnderlyingCourseOwnership() {
        Course course = new Course();
        course.setInstructorId(7L);
        Module module = new Module();
        module.setId(5L);
        module.setCourse(course);
        when(moduleRepository.findById(5L)).thenReturn(Optional.of(module));

        Module owned = accessControlService.getOwnedModule(5L, 7L, "INSTRUCTOR");

        assertSame(module, owned);
    }

    @Test
    void getOwnedLessonReturnsLessonForInstructor() {
        Course course = new Course();
        course.setInstructorId(7L);
        Module module = new Module();
        module.setCourse(course);
        Lesson lesson = new Lesson();
        lesson.setId(3L);
        lesson.setModule(module);
        when(lessonRepository.findById(3L)).thenReturn(Optional.of(lesson));

        Lesson owned = accessControlService.getOwnedLesson(3L, 7L, "INSTRUCTOR");

        assertSame(lesson, owned);
    }

    @Test
    void getOwnedQuizLoadsParentCourseBeforeReturningQuiz() {
        Quiz quiz = new Quiz();
        quiz.setId(2L);
        quiz.setCourseId(9L);
        Course course = new Course();
        course.setId(9L);
        course.setInstructorId(7L);
        when(quizRepository.findById(2L)).thenReturn(Optional.of(quiz));
        when(courseRepository.findById(9L)).thenReturn(Optional.of(course));

        Quiz owned = accessControlService.getOwnedQuiz(2L, 7L, "INSTRUCTOR");

        assertSame(quiz, owned);
    }

    @Test
    void getOwnedQuestionReturnsQuestionWhenQuizMatchesOwnership() {
        Quiz quiz = new Quiz();
        quiz.setId(4L);
        quiz.setCourseId(9L);
        Question question = new Question();
        question.setId(8L);
        question.setQuiz(quiz);
        Course course = new Course();
        course.setId(9L);
        course.setInstructorId(7L);

        when(questionRepository.findById(8L)).thenReturn(Optional.of(question));
        when(quizRepository.findById(4L)).thenReturn(Optional.of(quiz));
        when(courseRepository.findById(9L)).thenReturn(Optional.of(course));

        Question owned = accessControlService.getOwnedQuestion(8L, 7L, "INSTRUCTOR");

        assertSame(question, owned);
    }

    @Test
    void getOwnedAssignmentReturnsAssignmentForOwnedCourse() {
        Assignment assignment = new Assignment();
        assignment.setId(11L);
        assignment.setCourseId(9L);
        Course course = new Course();
        course.setId(9L);
        course.setInstructorId(7L);
        when(assignmentRepository.findById(11L)).thenReturn(Optional.of(assignment));
        when(courseRepository.findById(9L)).thenReturn(Optional.of(course));

        Assignment owned = accessControlService.getOwnedAssignment(11L, 7L, "INSTRUCTOR");

        assertSame(assignment, owned);
    }

    @Test
    void requireOwnershipAllowsAdminsAndRejectsMismatchedInstructor() {
        Course course = new Course();
        course.setInstructorId(9L);

        accessControlService.requireOwnership(course, 1L, "ADMIN");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> accessControlService.requireOwnership(course, 1L, "INSTRUCTOR")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void requireOwnershipRejectsNullCourse() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> accessControlService.requireOwnership(null, 1L, "ADMIN")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getOwnedCourseThrowsWhenMissing() {
        when(courseRepository.findById(55L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> accessControlService.getOwnedCourse(55L, 7L, "INSTRUCTOR")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getOwnedAssignmentThrowsWhenCourseMissing() {
        Assignment assignment = new Assignment();
        assignment.setId(11L);
        assignment.setCourseId(91L);
        when(assignmentRepository.findById(11L)).thenReturn(Optional.of(assignment));
        when(courseRepository.findById(91L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> accessControlService.getOwnedAssignment(11L, 7L, "INSTRUCTOR")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
