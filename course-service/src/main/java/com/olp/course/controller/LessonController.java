package com.olp.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.course.model.Course;
import com.olp.course.model.Lesson;
import com.olp.course.model.LessonResource;
import com.olp.course.service.AccessControlService;
import com.olp.course.service.FileStorageService;
import com.olp.course.repository.LessonRepository;
import com.olp.course.repository.LessonResourceRepository;
import com.olp.course.repository.ModuleRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping
@Transactional
public class LessonController {

    private final LessonRepository lessonRepository;
    private final ModuleRepository moduleRepository;
    private final LessonResourceRepository resourceRepository;
    private final AccessControlService accessControlService;
    private final FileStorageService fileStorageService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public LessonController(LessonRepository lessonRepository, ModuleRepository moduleRepository,
                            LessonResourceRepository resourceRepository,
                            AccessControlService accessControlService,
                            FileStorageService fileStorageService,
                            KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper) {
        this.lessonRepository = lessonRepository;
        this.moduleRepository = moduleRepository;
        this.resourceRepository = resourceRepository;
        this.accessControlService = accessControlService;
        this.fileStorageService = fileStorageService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/modules/{moduleId}/lessons")
    public ResponseEntity<Lesson> create(
            @PathVariable Long moduleId,
            @RequestBody Lesson lesson,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return moduleRepository.findById(moduleId).map(module -> {
            accessControlService.requireOwnership(module.getCourse(), userId, role);
            lesson.setModule(module);
            if (lesson.getLessonOrder() == null) {
                lesson.setLessonOrder(module.getLessons().size() + 1);
            }
            normalizeLesson(lesson);
            Lesson saved = lessonRepository.save(lesson);
            publishCourseContentAdded(module.getCourse(), "lesson", saved.getTitle(), saved.getId());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/modules/{moduleId}/lessons")
    public List<Lesson> getByModule(@PathVariable Long moduleId) {
        return lessonRepository.findByModuleIdOrderByLessonOrderAsc(moduleId);
    }

    @PutMapping("/modules/{moduleId}/lessons/reorder")
    public ResponseEntity<List<Lesson>> reorderLessons(
            @PathVariable Long moduleId,
            @RequestBody Map<String, List<Long>> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        List<Long> requestedIds = body.get("lessonIds");
        if (requestedIds == null || requestedIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        return moduleRepository.findById(moduleId).map(module -> {
            accessControlService.requireOwnership(module.getCourse(), userId, role);

            List<Lesson> currentLessons = lessonRepository.findByModuleIdOrderByLessonOrderAsc(moduleId);
            Map<Long, Lesson> lessonsById = new HashMap<>();
            for (Lesson lesson : currentLessons) {
                lessonsById.put(lesson.getId(), lesson);
            }

            Set<Long> uniqueRequestedIds = new LinkedHashSet<>(requestedIds);
            for (Long lessonId : uniqueRequestedIds) {
                if (!lessonsById.containsKey(lessonId)) {
                    return ResponseEntity.badRequest().<List<Lesson>>build();
                }
            }

            List<Lesson> reordered = new ArrayList<>();
            for (Long lessonId : uniqueRequestedIds) {
                reordered.add(lessonsById.get(lessonId));
            }
            for (Lesson lesson : currentLessons) {
                if (!uniqueRequestedIds.contains(lesson.getId())) {
                    reordered.add(lesson);
                }
            }

            for (int i = 0; i < reordered.size(); i++) {
                reordered.get(i).setLessonOrder(i + 1);
            }

            lessonRepository.saveAll(reordered);
            return ResponseEntity.ok(reordered);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/lessons/{id}")
    public ResponseEntity<Lesson> getById(@PathVariable Long id) {
        return lessonRepository.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/courses/{courseId}/preview-lessons")
    public ResponseEntity<List<Lesson>> getPreviewLessons(@PathVariable Long courseId) {
        return ResponseEntity.ok(lessonRepository.findByModuleCourseIdAndIsFreePreviewTrueOrderByLessonOrderAsc(courseId));
    }

    @PutMapping("/lessons/{id}")
    public ResponseEntity<Lesson> update(
            @PathVariable Long id,
            @RequestBody Lesson lessonDetails,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return lessonRepository.findById(id).map(lesson -> {
            accessControlService.requireOwnership(lesson.getModule().getCourse(), userId, role);
            lesson.setTitle(lessonDetails.getTitle());
            lesson.setContent(lessonDetails.getContent());
            lesson.setVideoUrl(lessonDetails.getVideoUrl());
            lesson.setDuration(lessonDetails.getDuration());
            lesson.setIsFreePreview(lessonDetails.getIsFreePreview());
            if (lessonDetails.getIsLocked() != null) {
                lesson.setIsLocked(lessonDetails.getIsLocked());
            }
            if (lessonDetails.getResources() != null) {
                lesson.setResources(lessonDetails.getResources());
            }
            normalizeLesson(lesson);
            return ResponseEntity.ok(lessonRepository.save(lesson));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/lessons/{id}/lock")
    public ResponseEntity<Lesson> lockLesson(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean locked,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return lessonRepository.findById(id).map(lesson -> {
            accessControlService.requireOwnership(lesson.getModule().getCourse(), userId, role);
            lesson.setIsLocked(locked);
            return ResponseEntity.ok(lessonRepository.save(lesson));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/lessons/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return lessonRepository.findById(id).map(lesson -> {
            accessControlService.requireOwnership(lesson.getModule().getCourse(), userId, role);
            lessonRepository.delete(lesson);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Lesson Resources ──

    @GetMapping("/lessons/{id}/resources")
    public ResponseEntity<List<LessonResource>> getResources(@PathVariable Long id) {
        if (!lessonRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resourceRepository.findByLessonIdOrderByDisplayOrderAsc(id));
    }

    @PostMapping("/lessons/{id}/resources")
    public ResponseEntity<LessonResource> addResource(
            @PathVariable Long id,
            @RequestBody LessonResource resource,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return lessonRepository.findById(id).map(lesson -> {
            accessControlService.requireOwnership(lesson.getModule().getCourse(), userId, role);
            resource.setLesson(lesson);
            if (resource.getDisplayOrder() == null) {
                resource.setDisplayOrder(lesson.getResources().size() + 1);
            }
            LessonResource saved = resourceRepository.save(resource);
            publishCourseContentAdded(lesson.getModule().getCourse(), "resource", saved.getTitle(), saved.getId());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/lessons/{lessonId}/resources/{resourceId}")
    public ResponseEntity<?> deleteResource(
            @PathVariable Long lessonId,
            @PathVariable Long resourceId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return lessonRepository.findById(lessonId).map(lesson -> {
            accessControlService.requireOwnership(lesson.getModule().getCourse(), userId, role);
            resourceRepository.deleteById(resourceId);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Upload a media file (PDF, PPT, video, etc.) for a lesson.
     * Stores the file and creates a LessonResource record automatically.
     */
    @PostMapping("/lessons/{id}/media")
    public ResponseEntity<LessonResource> uploadMedia(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return lessonRepository.findById(id).map(lesson -> {
            accessControlService.requireOwnership(lesson.getModule().getCourse(), userId, role);

            String fileName = fileStorageService.storeResourceFile(file);
            String fileDownloadUri = "/courses/uploads/" + fileName;

            LessonResource resource = new LessonResource();
            resource.setTitle(title != null ? title : file.getOriginalFilename());
            resource.setResourceUrl(fileDownloadUri);
            resource.setResourceType(resourceType != null ? resourceType : detectResourceType(file.getOriginalFilename()));
            resource.setFileSize(formatFileSize(file.getSize()));
            resource.setDisplayOrder(lesson.getResources().size() + 1);
            lesson.addResource(resource);
            LessonResource saved = resourceRepository.save(resource);
            publishCourseContentAdded(lesson.getModule().getCourse(), "resource", saved.getTitle(), saved.getId());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    private void publishCourseContentAdded(Course course, String contentType, String contentTitle, Long contentId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "COURSE_CONTENT_ADDED");
            event.put("courseId", course.getId());
            event.put("courseTitle", course.getTitle());
            event.put("instructorId", course.getInstructorId());
            event.put("contentType", contentType);
            event.put("contentId", contentId);
            event.put("contentTitle", contentTitle);
            kafkaTemplate.send("course-events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            System.err.println("Error publishing course content event: " + e.getMessage());
        }
    }

    private void normalizeLesson(Lesson lesson) {
        if (lesson.getIsFreePreview() == null) {
            lesson.setIsFreePreview(Boolean.FALSE);
        }
        if (lesson.getIsLocked() == null) {
            lesson.setIsLocked(Boolean.FALSE);
        }

        List<LessonResource> incomingResources = lesson.getResources() == null
                ? List.of()
                : new ArrayList<>(lesson.getResources());

        lesson.getResources().clear();
        int displayOrder = 1;
        for (LessonResource resource : incomingResources) {
            if (resource == null) {
                continue;
            }
            boolean hasUrl = resource.getResourceUrl() != null && !resource.getResourceUrl().isBlank();
            boolean hasTitle = resource.getTitle() != null && !resource.getTitle().isBlank();
            if (!hasUrl && !hasTitle) {
                continue;
            }
            if (resource.getResourceType() == null || resource.getResourceType().isBlank()) {
                resource.setResourceType("LINK");
            }
            if (resource.getDisplayOrder() == null) {
                resource.setDisplayOrder(displayOrder);
            }
            lesson.addResource(resource);
            displayOrder++;
        }
    }

    private String detectResourceType(String fileName) {
        if (fileName == null) return "OTHER";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "PPT";
        if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov") || lower.endsWith(".avi")) return "VIDEO";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "DOC";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "SPREADSHEET";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif")) return "IMAGE";
        return "OTHER";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
