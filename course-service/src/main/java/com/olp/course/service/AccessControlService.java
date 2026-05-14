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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccessControlService {

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AssignmentRepository assignmentRepository;

    public AccessControlService(
            CourseRepository courseRepository,
            ModuleRepository moduleRepository,
            LessonRepository lessonRepository,
            QuizRepository quizRepository,
            QuestionRepository questionRepository,
            AssignmentRepository assignmentRepository
    ) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.assignmentRepository = assignmentRepository;
    }

    public boolean isAdmin(String role) {
        return "ADMIN".equalsIgnoreCase(role);
    }

    public boolean isInstructor(String role) {
        return "INSTRUCTOR".equalsIgnoreCase(role);
    }

    public void requireInstructorOrAdmin(String role) {
        if (!isInstructor(role) && !isAdmin(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Instructor access is required");
        }
    }

    public Course getOwnedCourse(Long courseId, Long userId, String role) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        requireOwnership(course, userId, role);
        return course;
    }

    public Module getOwnedModule(Long moduleId, Long userId, String role) {
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module not found"));
        requireOwnership(module.getCourse(), userId, role);
        return module;
    }

    public Lesson getOwnedLesson(Long lessonId, Long userId, String role) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found"));
        requireOwnership(lesson.getModule().getCourse(), userId, role);
        return lesson;
    }

    public Quiz getOwnedQuiz(Long quizId, Long userId, String role) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
        Course course = courseRepository.findById(quiz.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        requireOwnership(course, userId, role);
        return quiz;
    }

    public Question getOwnedQuestion(Long questionId, Long userId, String role) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
        Quiz quiz = getOwnedQuiz(question.getQuiz().getId(), userId, role);
        if (!quiz.getId().equals(question.getQuiz().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Question does not belong to your course");
        }
        return question;
    }

    public Assignment getOwnedAssignment(Long assignmentId, Long userId, String role) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        Course course = courseRepository.findById(assignment.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        requireOwnership(course, userId, role);
        return assignment;
    }

    public void requireOwnership(Course course, Long userId, String role) {
        if (course == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
        if (isAdmin(role)) {
            return;
        }
        if (!isInstructor(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Instructor access is required");
        }
        if (userId == null || course.getInstructorId() == null || !course.getInstructorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this course");
        }
    }
}
