package com.olp.course.controller;

import com.olp.course.model.Course;
import com.olp.course.dto.CourseDto;
import com.olp.course.dto.CourseMapper;
import com.olp.course.service.CourseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.olp.course.service.FileStorageService;
import org.springframework.web.multipart.MultipartFile;
// import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseService courseService;
    private final FileStorageService fileStorageService;

    public CourseController(CourseService courseService, FileStorageService fileStorageService) {
        this.courseService = courseService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    public ResponseEntity<CourseDto> create(
            @RequestBody Course course,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String userName
    ) {
        return ResponseEntity.ok(CourseMapper.toDto(courseService.createCourse(course, userId, userName, role)));
    }

    @GetMapping
    public ResponseEntity<List<CourseDto>> getAll(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return ResponseEntity.ok(CourseMapper.toDtoList(courseService.getAllCourses(userId, role)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDto> getById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return courseService.getCourseById(id, userId, role)
                .map(CourseMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/published")
    public ResponseEntity<List<CourseDto>> getPublished(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "instructorId", required = false) Long instructorId,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "q", required = false) String query) {
        return ResponseEntity.ok(CourseMapper.toDtoList(courseService.getPublishedCourses(category, level, instructorId, language, query)));
    }

    @GetMapping("/featured")
    public ResponseEntity<List<CourseDto>> getFeatured() {
        return ResponseEntity.ok(CourseMapper.toDtoList(courseService.getFeaturedCourses()));
    }

    @GetMapping("/category/{cat}")
    public ResponseEntity<List<CourseDto>> getByCategory(@PathVariable String cat) {
        return ResponseEntity.ok(CourseMapper.toDtoList(courseService.getCoursesByCategory(cat)));
    }

    @GetMapping("/search")
    public ResponseEntity<List<CourseDto>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(CourseMapper.toDtoList(courseService.searchCourses(query)));
    }

    @GetMapping("/me")
    public ResponseEntity<List<CourseDto>> getMyCreated(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return ResponseEntity.ok(CourseMapper.toDtoList(courseService.getMyCreatedCourses(userId, role)));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<CourseDto>> getPending(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(CourseMapper.toDtoList(courseService.getPendingCourses(role)));
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return courseService.getCourseById(id, userId, role)
                .map(course -> {
                    Map<String, Object> stats = new LinkedHashMap<>();
                    stats.put("courseId", course.getId());
                    stats.put("status", course.getStatus());
                    stats.put("reviewStatus", course.getReviewStatus());
                    stats.put("studentsCount", course.getStudentsCount() != null ? course.getStudentsCount() : 0);
                    stats.put("rating", course.getRating() != null ? course.getRating() : 0.0);
                    stats.put("modulesCount", course.getModules() != null ? course.getModules().size() : 0);
                    return ResponseEntity.ok(stats);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseDto> update(
            @PathVariable Long id,
            @RequestBody Course courseDetails,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return courseService.updateCourse(id, courseDetails, userId, role)
                .map(CourseMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/publish")
    public ResponseEntity<CourseDto> publish(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return courseService.publishCourse(id, userId, role)
                .map(CourseMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<CourseDto> approve(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return courseService.approveCourse(id, userId, role)
                .map(CourseMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<CourseDto> reject(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return courseService.rejectCourse(id, body.get("reason"), userId, role)
                .map(CourseMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/unpublish")
    public ResponseEntity<CourseDto> unpublish(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return courseService.unpublishCourse(id, userId, role)
                .map(CourseMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        if (courseService.deleteCourse(id, userId, role)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/thumbnail")
    public ResponseEntity<CourseDto> uploadThumbnail(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        String fileName = fileStorageService.storeImage(file);
        
        // Use a relative path so it works through any gateway/proxy
        String fileDownloadUri = "/courses/uploads/" + fileName;

        return courseService.getCourseById(id, userId, role)
                .map(course -> {
                    course.setThumbnail(fileDownloadUri);
                    return ResponseEntity.ok(CourseMapper.toDto(courseService.updateCourse(id, course, userId, role).get()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/admin/instructor-analytics")
    public ResponseEntity<List<java.util.Map<String, Object>>> getInstructorAnalytics(
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        List<Course> allCourses = courseService.getAllCoursesForAdminAnalytics(role);
        java.util.Map<Long, java.util.Map<String, Object>> analyticsMap = new java.util.HashMap<>();

        for (Course c : allCourses) {
            Long instrId = c.getInstructorId();
            if (instrId == null) continue;

            java.util.Map<String, Object> stats = analyticsMap.getOrDefault(instrId, new java.util.HashMap<>());
            stats.putIfAbsent("instructorId", instrId);
            stats.putIfAbsent("instructorName", c.getInstructorName());
            stats.put("totalCourses", (int) stats.getOrDefault("totalCourses", 0) + 1);
            stats.put("totalStudents", (int) stats.getOrDefault("totalStudents", 0) + (c.getStudentsCount() != null ? c.getStudentsCount() : 0));
            stats.put("publishedCourses", (int) stats.getOrDefault("publishedCourses", 0) + ("PUBLISHED".equals(c.getStatus()) ? 1 : 0));
            
            analyticsMap.put(instrId, stats);
        }

        return ResponseEntity.ok(new java.util.ArrayList<>(analyticsMap.values()));
    }
}
