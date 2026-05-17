package com.olp.course.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertNotNull;
// import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.olp.course.model.Course;
import com.olp.course.service.CourseService;
import com.olp.course.service.FileStorageService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class CourseControllerTest {

  @Mock
  private CourseService courseService;

  @Mock
  private FileStorageService fileStorageService;

  private CourseController courseController;

  @BeforeEach
  void setUp() {
    courseController = new CourseController(courseService, fileStorageService);
  }

  @Test
  void getByIdReturnsNotFoundWhenCourseDoesNotExist() {
    when(courseService.getCourseById(9L, 1L, "STUDENT"))
      .thenReturn(Optional.empty());

    ResponseEntity<?> response = courseController.getById(9L, 1L, "STUDENT");

    assertEquals(404, response.getStatusCode().value());
  }

  @Test
  void getStatsReturnsDefaultValuesForNullMetrics() {
    Course course = new Course();
    course.setId(12L);
    course.setStatus("DRAFT");
    course.setReviewStatus("PENDING");
    when(courseService.getCourseById(12L, 5L, "INSTRUCTOR"))
      .thenReturn(Optional.of(course));

    ResponseEntity<Map<String, Object>> response = courseController.getStats(
      12L,
      5L,
      "INSTRUCTOR"
    );

    assertEquals(200, response.getStatusCode().value());
    assertEquals(0, response.getBody().get("studentsCount"));
    assertEquals(0.0, response.getBody().get("rating"));
    assertEquals(0, response.getBody().get("modulesCount"));
  }

  @Test
  void deleteReturnsOkWhenServiceDeletesCourse() {
    when(courseService.deleteCourse(3L, 5L, "INSTRUCTOR")).thenReturn(true);

    ResponseEntity<?> response = courseController.delete(3L, 5L, "INSTRUCTOR");

    assertEquals(200, response.getStatusCode().value());
  }

  @Test
  void uploadThumbnailStoresFileAndUpdatesCourse() {
    Course course = new Course();
    course.setId(3L);
    when(fileStorageService.storeImage(org.mockito.ArgumentMatchers.any()))
      .thenReturn("cover.png");
    when(courseService.getCourseById(3L, 5L, "INSTRUCTOR"))
      .thenReturn(Optional.of(course));
    when(
      courseService.updateCourse(
        org.mockito.ArgumentMatchers.eq(3L),
        org.mockito.ArgumentMatchers.any(Course.class),
        org.mockito.ArgumentMatchers.eq(5L),
        org.mockito.ArgumentMatchers.eq("INSTRUCTOR")
      )
    )
      .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));

    MockMultipartFile file = new MockMultipartFile(
      "file",
      "cover.png",
      "image/png",
      "img".getBytes()
    );
    ResponseEntity<?> response = courseController.uploadThumbnail(
      3L,
      file,
      5L,
      "INSTRUCTOR"
    );

    assertEquals(200, response.getStatusCode().value());
    assertEquals("/courses/uploads/cover.png", course.getThumbnail());
  }

  @Test
  void getInstructorAnalyticsAggregatesCourseCountsByInstructor() {
    Course first = new Course();
    first.setInstructorId(10L);
    first.setInstructorName("Asha");
    first.setStudentsCount(12);
    first.setStatus("PUBLISHED");

    Course second = new Course();
    second.setInstructorId(10L);
    second.setInstructorName("Asha");
    second.setStudentsCount(8);
    second.setStatus("DRAFT");

    when(courseService.getAllCoursesForAdminAnalytics("ADMIN"))
      .thenReturn(List.of(first, second));

    ResponseEntity<List<Map<String, Object>>> response = courseController.getInstructorAnalytics(
      "ADMIN"
    );

    assertEquals(200, response.getStatusCode().value());
    assertEquals(1, response.getBody().size());
    assertEquals(2, response.getBody().get(0).get("totalCourses"));
    assertEquals(20, response.getBody().get(0).get("totalStudents"));
    assertEquals(1, response.getBody().get(0).get("publishedCourses"));
  }
}
