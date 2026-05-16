package com.olp.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.course.model.Course;
import com.olp.course.model.Lesson;
import com.olp.course.model.LessonResource;
import com.olp.course.model.Module;
import com.olp.course.repository.CourseRepository;
import com.olp.course.repository.LessonRepository;
import com.olp.course.repository.LessonResourceRepository;
import com.olp.course.repository.ModuleRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonResourceRepository lessonResourceRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private RestTemplate restTemplate;

    private CourseService courseService;

    @BeforeEach
    void setUp() {
        courseService = new CourseService(
                courseRepository,
                moduleRepository,
                lessonRepository,
                lessonResourceRepository,
                kafkaTemplate,
                new ObjectMapper(),
                accessControlService,
                restTemplate
        );
    }

    @Test
    void getAllCoursesReturnsOnlyOwnedCoursesForInstructor() {
        Course first = new Course();
        first.setId(1L);
        Course second = new Course();
        second.setId(2L);
        List<Course> ownedCourses = List.of(first, second);
        when(accessControlService.isInstructor("INSTRUCTOR")).thenReturn(true);
        when(accessControlService.isAdmin("INSTRUCTOR")).thenReturn(false);
        when(courseRepository.findByInstructorId(7L)).thenReturn(ownedCourses);
        when(moduleRepository.findByCourseIdInOrderByCourseIdAscModuleOrderAsc(List.of(1L, 2L))).thenReturn(List.of());

        List<Course> result = courseService.getAllCourses(7L, "INSTRUCTOR");

        assertSame(ownedCourses, result);
        verify(courseRepository).findByInstructorId(7L);
        verify(courseRepository, never()).findAll();
    }

    @Test
    void getAllCoursesRejectsInstructorWithoutUserId() {
        when(accessControlService.isInstructor("INSTRUCTOR")).thenReturn(true);
        when(accessControlService.isAdmin("INSTRUCTOR")).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> courseService.getAllCourses(null, "INSTRUCTOR")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(courseRepository, never()).findAll();
    }

    @Test
    void getCourseByIdEnforcesInstructorOwnership() {
        Course course = new Course();
        course.setId(5L);
        course.setInstructorId(11L);
        when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
        when(moduleRepository.findByCourseIdInOrderByCourseIdAscModuleOrderAsc(List.of(5L))).thenReturn(List.of());
        when(accessControlService.isInstructor("INSTRUCTOR")).thenReturn(true);
        when(accessControlService.isAdmin("INSTRUCTOR")).thenReturn(false);

        Optional<Course> result = courseService.getCourseById(5L, 11L, "INSTRUCTOR");

        assertSame(course, result.orElseThrow());
        verify(courseRepository).findById(5L);
    }

    @Test
    void createCourseSetsInstructorMetadataBeforeSave() {
        Course input = new Course();
        input.setTitle("Spring Security");
        input.setCategory("Web Development");

        Course saved = new Course();
        saved.setId(99L);
        saved.setInstructorId(3L);
        saved.setInstructorName("Asha");
        when(courseRepository.save(any(Course.class))).thenReturn(saved);

        Course result = courseService.createCourse(input, 3L, "Asha", "INSTRUCTOR");

        assertEquals(99L, result.getId());
        assertEquals(3L, input.getInstructorId());
        assertEquals("Asha", input.getInstructorName());
        assertEquals("Web Development", input.getCategory());
        verify(accessControlService).requireInstructorOrAdmin("INSTRUCTOR");
    }

    @Test
    void getAllCoursesCachesInstructorVerificationLookupsPerInstructor() {
        Course first = new Course();
        first.setId(1L);
        first.setInstructorId(21L);
        Course second = new Course();
        second.setId(2L);
        second.setInstructorId(21L);
        Course third = new Course();
        third.setId(3L);
        third.setInstructorId(22L);
        List<Course> courses = List.of(first, second, third);

        when(accessControlService.isAdmin("ADMIN")).thenReturn(true);
        when(courseRepository.findAll()).thenReturn(courses);
        when(moduleRepository.findByCourseIdInOrderByCourseIdAscModuleOrderAsc(List.of(1L, 2L, 3L))).thenReturn(List.of());
        when(restTemplate.getForObject("http://user-service/users/21", Map.class))
                .thenReturn(Map.of("instructorVerificationStatus", "APPROVED"));
        when(restTemplate.getForObject("http://user-service/users/22", Map.class))
                .thenReturn(Map.of("instructorVerificationStatus", "PENDING"));

        List<Course> result = courseService.getAllCourses(1L, "ADMIN");

        assertEquals(Boolean.TRUE, result.get(0).getInstructorVerified());
        assertEquals(Boolean.TRUE, result.get(1).getInstructorVerified());
        assertEquals(Boolean.FALSE, result.get(2).getInstructorVerified());
        verify(restTemplate, times(1)).getForObject("http://user-service/users/21", Map.class);
        verify(restTemplate, times(1)).getForObject("http://user-service/users/22", Map.class);
    }

    @Test
    void getPublishedCoursesFiltersByCategory() {
        Course programming = new Course();
        programming.setId(1L);
        programming.setTitle("Java");
        programming.setCategory("Programming");
        programming.setStatus("PUBLISHED");

        Course design = new Course();
        design.setId(2L);
        design.setTitle("UI Basics");
        design.setCategory("Design");
        design.setStatus("PUBLISHED");

        when(courseRepository.findByStatus("PUBLISHED")).thenReturn(List.of(programming, design));
        when(moduleRepository.findByCourseIdInOrderByCourseIdAscModuleOrderAsc(List.of(1L, 2L))).thenReturn(List.of());

        List<Course> result = courseService.getPublishedCourses("Programming", null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("Programming", result.get(0).getCategory());
    }

    @Test
    void updateCoursePreservesThumbnailWhenPatchOmitsIt() {
        Course existing = new Course();
        existing.setId(7L);
        existing.setInstructorId(12L);
        existing.setThumbnail("/courses/uploads/cover.png");

        Course patch = new Course();
        patch.setTitle("Updated title");
        patch.setDescription("Updated description");
        patch.setCategory("Programming");

        when(courseRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Course updated = courseService.updateCourse(7L, patch, 12L, "INSTRUCTOR").orElseThrow();

        assertEquals("/courses/uploads/cover.png", updated.getThumbnail());
        assertEquals("Programming", updated.getCategory());
        verify(accessControlService).requireOwnership(existing, 12L, "INSTRUCTOR");
    }

    @Test
    void getCourseByIdHydratesModulesLessonsAndResources() {
        Course course = new Course();
        course.setId(12L);
        course.setInstructorId(44L);
        course.setStatus("PUBLISHED");

        Module module = new Module();
        module.setId(101L);
        module.setTitle("Getting Started");
        module.setCourse(course);

        Lesson lesson = new Lesson();
        lesson.setId(501L);
        lesson.setTitle("Module content");
        lesson.setModule(module);

        LessonResource resource = new LessonResource();
        resource.setId(901L);
        resource.setTitle("Kickoff PDF");
        resource.setLesson(lesson);

        when(courseRepository.findById(12L)).thenReturn(Optional.of(course));
        when(moduleRepository.findByCourseIdInOrderByCourseIdAscModuleOrderAsc(List.of(12L))).thenReturn(List.of(module));
        when(lessonRepository.findByModuleIdInOrderByModuleIdAscLessonOrderAsc(List.of(101L))).thenReturn(List.of(lesson));
        when(lessonResourceRepository.findByLessonIdInOrderByLessonIdAscDisplayOrderAsc(List.of(501L))).thenReturn(List.of(resource));
        when(accessControlService.isAdmin("STUDENT")).thenReturn(false);
        when(accessControlService.isInstructor("STUDENT")).thenReturn(false);

        Course hydrated = courseService.getCourseById(12L, 5L, "STUDENT").orElseThrow();

        assertEquals(1, hydrated.getModules().size());
        assertEquals(1, hydrated.getModules().get(0).getLessons().size());
        assertEquals(1, hydrated.getModules().get(0).getLessons().get(0).getResources().size());
        assertEquals("Kickoff PDF", hydrated.getModules().get(0).getLessons().get(0).getResources().get(0).getTitle());
    }
}
