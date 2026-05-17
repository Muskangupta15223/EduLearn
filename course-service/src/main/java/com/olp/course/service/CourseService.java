package com.olp.course.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.course.constant.CourseConstants;
import com.olp.course.model.Course;
import com.olp.course.model.Lesson;
import com.olp.course.model.LessonResource;
import com.olp.course.model.Module;
import com.olp.course.repository.CourseRepository;
import com.olp.course.repository.LessonRepository;
import com.olp.course.repository.LessonResourceRepository;
import com.olp.course.repository.ModuleRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseService {
    private static final String REVIEW_STATUS_UNPUBLISHED = "UNPUBLISHED";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String REVIEW_STATUS_REJECTED = "REJECTED";
    private static final String EVENT_COURSE_APPROVED = "COURSE_APPROVED";
    private static final String EVENT_COURSE_REJECTED = "COURSE_REJECTED";
    private static final String EVENT_COURSE_UNPUBLISHED = "COURSE_UNPUBLISHED";

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final LessonResourceRepository lessonResourceRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AccessControlService accessControlService;
    private final RestTemplate restTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public CourseService(CourseRepository courseRepository,
                         ModuleRepository moduleRepository,
                         LessonRepository lessonRepository,
                         LessonResourceRepository lessonResourceRepository,
                         KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper,
                         AccessControlService accessControlService,
                         RestTemplate restTemplate) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.lessonResourceRepository = lessonResourceRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.accessControlService = accessControlService;
        this.restTemplate = restTemplate;
    }

    public Course createCourse(Course course, Long instructorId, String instructorName, String role) {
        accessControlService.requireInstructorOrAdmin(role);
        if (instructorId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
        }
        course.setInstructorId(instructorId);
        course.setInstructorName(instructorName == null || instructorName.isBlank() ? "Instructor" : instructorName);
        Course saved = courseRepository.save(course);
        if (accessControlService.isInstructor(role)) {
            publishCourseEvent(saved, CourseConstants.EVENT_COURSE_APPROVAL_REQUEST, "CREATED");
        }
        return saved;
    }

    public List<Course> getAllCourses(Long userId, String role) {
        if (accessControlService.isAdmin(role)) {
            return enrichInstructorVerification(hydrateCourseContent(courseRepository.findAll()));
        }
        if (accessControlService.isInstructor(role)) {
            if (userId == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
            }
            return enrichInstructorVerification(hydrateCourseContent(courseRepository.findByInstructorId(userId)));
        }
        return enrichInstructorVerification(hydrateCourseContent(courseRepository.findByStatus(CourseConstants.STATUS_PUBLISHED)));
    }

    @Transactional
    public Optional<Course> getCourseById(Long id, Long userId, String role) {
        Optional<Course> course = courseRepository.findById(id).map(this::hydrateCourseContent);
        if (course.isEmpty()) {
            return Optional.empty();
        }
        if (accessControlService.isAdmin(role)) {
            return course.map(this::enrichInstructorVerification);
        }
        if (accessControlService.isInstructor(role)) {
            Course current = course.get();
            if (current.getInstructorId() != null && current.getInstructorId().equals(userId)) {
                return course.map(this::enrichInstructorVerification);
            }
            if (CourseConstants.STATUS_PUBLISHED.equals(current.getStatus())) {
                return course.map(this::enrichInstructorVerification);
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this course");
        }
        if (!CourseConstants.STATUS_PUBLISHED.equals(course.get().getStatus())) {
            return Optional.empty();
        }
        return course.map(this::enrichInstructorVerification);
    }

    public List<Course> getPublishedCourses() {
        return enrichInstructorVerification(hydrateCourseContent(courseRepository.findByStatus(CourseConstants.STATUS_PUBLISHED)));
    }

    public List<Course> getPublishedCourses(String category, String level, Long instructorId, String language, String query) {
        return enrichInstructorVerification(hydrateCourseContent(courseRepository.findByStatus(CourseConstants.STATUS_PUBLISHED)).stream()
                .filter(course -> matchesPublishedFilters(course, category, level, instructorId, language))
                .filter(course -> matchesCourseQuery(course, query))
                .collect(Collectors.toList()));
    }

    public List<Course> getFeaturedCourses() {
        List<Course> courses = hydrateCourseContent(courseRepository.findByStatus(CourseConstants.STATUS_PUBLISHED));
        return enrichInstructorVerification(courses.size() > 6 ? courses.subList(0, 6) : courses);
    }

    public List<Course> getCoursesByCategory(String category) {
        return enrichInstructorVerification(hydrateCourseContent(courseRepository.findByCategoryAndStatus(category, CourseConstants.STATUS_PUBLISHED)));
    }

    public List<Course> searchCourses(String query) {
        return enrichInstructorVerification(hydrateCourseContent(courseRepository.findByTitleContainingIgnoreCaseAndStatus(query, CourseConstants.STATUS_PUBLISHED)));
    }

    @Transactional
    public List<Course> getMyCreatedCourses(Long instructorId, String role) {
        accessControlService.requireInstructorOrAdmin(role);
        if (instructorId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user id");
        }
        return enrichInstructorVerification(hydrateCourseContent(courseRepository.findByInstructorId(instructorId)));
    }

    public List<Course> getPendingCourses(String role) {
        requireAdmin(role);
        return enrichInstructorVerification(hydrateCourseContent(courseRepository.findByStatus(CourseConstants.STATUS_PENDING)));
    }

    public List<Course> getAllCoursesForAdminAnalytics(String role) {
        requireAdmin(role);
        return enrichInstructorVerification(hydrateCourseContent(courseRepository.findAll()));
    }

    public Optional<Course> updateCourse(Long id, Course courseDetails, Long userId, String role) {
        return courseRepository.findById(id).map(course -> {
            accessControlService.requireOwnership(course, userId, role);
            course.setTitle(courseDetails.getTitle());
            course.setDescription(courseDetails.getDescription());
            course.setPrice(courseDetails.getPrice());
            course.setLevel(courseDetails.getLevel());
            course.setLanguage(courseDetails.getLanguage());
            course.setCategory(courseDetails.getCategory());
            if (courseDetails.getThumbnail() != null) {
                course.setThumbnail(courseDetails.getThumbnail());
            }
            Course saved = courseRepository.save(course);
            if (accessControlService.isInstructor(role)) {
                publishCourseEvent(saved, CourseConstants.EVENT_COURSE_APPROVAL_REQUEST, "UPDATED");
            }
            return saved;
        });
    }

    public Optional<Course> publishCourse(Long id, Long userId, String role) {
        return courseRepository.findById(id).map(course -> {
            accessControlService.requireOwnership(course, userId, role);
            course.setStatus(CourseConstants.STATUS_PENDING);
            course.setReviewStatus(CourseConstants.STATUS_PENDING);
            course.setReviewComment(null);
            course.setReviewedAt(null);
            course.setReviewedBy(null);
            course.setSubmittedForReviewAt(LocalDateTime.now());
            Course saved = courseRepository.save(course);
            publishCourseEvent(saved, CourseConstants.EVENT_COURSE_APPROVAL_REQUEST, "SUBMITTED");
            return saved;
        });
    }

    public Optional<Course> approveCourse(Long id, Long reviewerId, String role) {
        requireAdmin(role);
        return courseRepository.findById(id).map(course -> {
            course.setStatus(CourseConstants.STATUS_PUBLISHED);
            course.setReviewStatus(CourseConstants.STATUS_APPROVED);
            course.setReviewComment("Approved for publication");
            course.setReviewedAt(LocalDateTime.now());
            course.setReviewedBy(reviewerId);
            Course saved = courseRepository.save(course);
            publishCourseEvent(saved, EVENT_COURSE_APPROVED, null);
            return saved;
        });
    }

    public Optional<Course> rejectCourse(Long id, String reason, Long reviewerId, String role) {
        requireAdmin(role);
        return courseRepository.findById(id).map(course -> {
            course.setStatus(STATUS_REJECTED);
            course.setReviewStatus(REVIEW_STATUS_REJECTED);
            course.setReviewComment(reason == null || reason.isBlank() ? "Course rejected during moderation" : reason.trim());
            course.setReviewedAt(LocalDateTime.now());
            course.setReviewedBy(reviewerId);
            Course saved = courseRepository.save(course);
            publishCourseEvent(saved, EVENT_COURSE_REJECTED, null);
            return saved;
        });
    }

    public Optional<Course> unpublishCourse(Long id, Long reviewerId, String role) {
        return courseRepository.findById(id).map(course -> {
            if (accessControlService.isAdmin(role)) {
                course.setReviewedBy(reviewerId);
                course.setReviewComment("Unpublished by admin");
                course.setReviewedAt(LocalDateTime.now());
            } else {
                accessControlService.requireOwnership(course, reviewerId, role);
                course.setReviewComment("Unpublished by instructor");
            }
            course.setStatus(STATUS_DRAFT);
            course.setReviewStatus(REVIEW_STATUS_UNPUBLISHED);
            Course saved = courseRepository.save(course);
            publishCourseEvent(saved, EVENT_COURSE_UNPUBLISHED, null);
            return saved;
        });
    }

    public boolean deleteCourse(Long id, Long userId, String role) {
        return courseRepository.findById(id).map(course -> {
            accessControlService.requireOwnership(course, userId, role);
            courseRepository.delete(course);
            return true;
        }).orElse(false);
    }

    private void requireAdmin(String role) {
        if (!accessControlService.isAdmin(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private boolean matchesPublishedFilters(Course course, String category, String level, Long instructorId, String language) {
        return matchesIgnoreCase(course.getCategory(), category)
                && matchesIgnoreCase(course.getLevel(), level)
                && (instructorId == null || instructorId.equals(course.getInstructorId()))
                && matchesIgnoreCase(course.getLanguage(), language);
    }

    private boolean matchesIgnoreCase(String actualValue, String filterValue) {
        return filterValue == null
                || filterValue.isBlank()
                || (actualValue != null && filterValue.equalsIgnoreCase(actualValue));
    }

    private boolean matchesCourseQuery(Course course, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String normalizedQuery = query.toLowerCase();
        return containsIgnoreCase(course.getTitle(), normalizedQuery)
                || containsIgnoreCase(course.getDescription(), normalizedQuery)
                || containsIgnoreCase(course.getCategory(), normalizedQuery)
                || containsIgnoreCase(course.getLanguage(), normalizedQuery)
                || containsIgnoreCase(course.getInstructorName(), normalizedQuery);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase().contains(normalizedQuery);
    }

    private Course hydrateCourseContent(Course course) {
        if (course == null || course.getId() == null) {
            return course;
        }
        hydrateCourseContent(List.of(course));
        return course;
    }

    private List<Course> hydrateCourseContent(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return courses;
        }

        Map<Long, Course> coursesById = courses.stream()
                .filter(course -> course.getId() != null)
                .peek(this::detachIfManaged)
                .collect(Collectors.toMap(Course::getId, course -> course, (left, right) -> left, LinkedHashMap::new));
        if (coursesById.isEmpty()) {
            return courses;
        }

        List<Module> modules = moduleRepository.findByCourseIdInOrderByCourseIdAscModuleOrderAsc(new ArrayList<>(coursesById.keySet()));
        modules.forEach(module -> {
            detachIfManaged(module);
            module.setCourse(coursesById.get(module.getCourse().getId()));
            module.setLessons(new ArrayList<>());
        });

        List<Long> moduleIds = modules.stream()
                .map(Module::getId)
                .filter(Objects::nonNull)
                .toList();

        List<Lesson> lessons = moduleIds.isEmpty()
                ? List.of()
                : lessonRepository.findByModuleIdInOrderByModuleIdAscLessonOrderAsc(moduleIds);
        lessons.forEach(lesson -> {
            detachIfManaged(lesson);
            lesson.setResourcesForHydration(new ArrayList<>());
        });

        Map<Long, List<Lesson>> lessonsByModuleId = lessons.stream()
                .collect(Collectors.groupingBy(lesson -> lesson.getModule().getId(), LinkedHashMap::new, Collectors.toList()));

        List<Long> lessonIds = lessons.stream()
                .map(Lesson::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, List<LessonResource>> resourcesByLessonId = lessonIds.isEmpty()
                ? Collections.emptyMap()
                : lessonResourceRepository.findByLessonIdInOrderByLessonIdAscDisplayOrderAsc(lessonIds).stream()
                .peek(this::detachIfManaged)
                .collect(Collectors.groupingBy(resource -> resource.getLesson().getId(), LinkedHashMap::new, Collectors.toList()));

        lessons.forEach(lesson -> {
            List<LessonResource> resources = new ArrayList<>(resourcesByLessonId.getOrDefault(lesson.getId(), List.of()));
            lesson.setResourcesForHydration(resources);
        });

        modules.forEach(module -> {
            List<Lesson> moduleLessons = new ArrayList<>(lessonsByModuleId.getOrDefault(module.getId(), List.of()));
            moduleLessons.forEach(lesson -> lesson.setModule(module));
            module.setLessons(moduleLessons);
        });

        Map<Long, List<Module>> modulesByCourseId = modules.stream()
                .collect(Collectors.groupingBy(module -> module.getCourse().getId(), LinkedHashMap::new, Collectors.toList()));

        coursesById.forEach((courseId, course) -> {
            List<Module> courseModules = new ArrayList<>(modulesByCourseId.getOrDefault(courseId, List.of()));
            courseModules.forEach(module -> module.setCourse(course));
            course.setModules(courseModules);
        });

        return courses;
    }

    private void detachIfManaged(Object entity) {
        if (entityManager != null && entity != null) {
            entityManager.detach(entity);
        }
    }

    private List<Course> enrichInstructorVerification(List<Course> courses) {
        Map<Long, String> verificationCache = new HashMap<>();
        courses.forEach(course -> enrichInstructorVerification(course, verificationCache));
        return courses;
    }

    private Course enrichInstructorVerification(Course course) {
        return enrichInstructorVerification(course, new HashMap<>());
    }

    private Course enrichInstructorVerification(Course course, Map<Long, String> verificationCache) {
        if (course == null || course.getInstructorId() == null) {
            return course;
        }
        Long instructorId = course.getInstructorId();
        if (verificationCache.containsKey(instructorId)) {
            String cachedStatus = verificationCache.get(instructorId);
            course.setInstructorVerificationStatus(cachedStatus);
            course.setInstructorVerified(CourseConstants.STATUS_APPROVED.equalsIgnoreCase(cachedStatus));
            return course;
        }
        try {
            InstructorProfileResponse profile = restTemplate.getForObject(
                    "http://user-service/users/" + instructorId,
                    InstructorProfileResponse.class
            );
            String verificationStatus = profile == null ? null : profile.instructorVerificationStatus();
            verificationCache.put(instructorId, verificationStatus);
            course.setInstructorVerificationStatus(verificationStatus);
            course.setInstructorVerified(CourseConstants.STATUS_APPROVED.equalsIgnoreCase(verificationStatus));
        } catch (RestClientException ex) {
            verificationCache.put(instructorId, null);
            course.setInstructorVerified(false);
        }
        return course;
    }

    private void publishCourseEvent(Course course, String eventType, String action) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("courseId", course.getId());
            event.put("title", course.getTitle());
            event.put("instructorId", course.getInstructorId());
            event.put("instructorName", course.getInstructorName());
            event.put("status", course.getStatus());
            event.put("reviewStatus", course.getReviewStatus());
            event.put("reviewComment", course.getReviewComment());
            event.put("eventType", eventType);
            event.put("action", action);
            event.put("timestamp", LocalDateTime.now().toString());
            kafkaTemplate.send("course-events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            System.err.println("Error publishing course moderation event: " + e.getMessage());
        }
    }

    record InstructorProfileResponse(String instructorVerificationStatus) {
    }
}
