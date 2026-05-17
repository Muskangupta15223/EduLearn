package com.olp.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.course.model.Module;
import com.olp.course.model.Course;
import com.olp.course.model.Lesson;
import com.olp.course.model.LessonResource;
import com.olp.course.repository.LessonRepository;
import com.olp.course.repository.LessonResourceRepository;
import com.olp.course.service.AccessControlService;
import com.olp.course.service.FileStorageService;
import com.olp.course.repository.CourseRepository;
import com.olp.course.repository.ModuleRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping
@Transactional
public class ModuleController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleController.class);

    private final ModuleRepository moduleRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonResourceRepository resourceRepository;
    private final AccessControlService accessControlService;
    private final FileStorageService fileStorageService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public ModuleController(ModuleRepository moduleRepository, CourseRepository courseRepository,
                            LessonRepository lessonRepository,
                            LessonResourceRepository resourceRepository,
                            AccessControlService accessControlService,
                            FileStorageService fileStorageService,
                            KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper) {
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.resourceRepository = resourceRepository;
        this.accessControlService = accessControlService;
        this.fileStorageService = fileStorageService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/courses/{courseId}/modules")
    public ResponseEntity<Module> create(
            @PathVariable Long courseId,
            @RequestBody Module module,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return courseRepository.findById(courseId).map(course -> {
            accessControlService.requireOwnership(course, userId, role);
            module.setCourse(course);
            if (module.getModuleOrder() == null) {
                module.setModuleOrder(course.getModules().size() + 1);
            }
            normalizeModule(module);
            Module saved = moduleRepository.save(module);
            publishCourseContentAdded(course, "module", saved.getTitle(), saved.getId());
            return ResponseEntity.ok(hydrateModule(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/courses/{courseId}/modules")
    public List<Module> getByCourse(@PathVariable Long courseId) {
        return hydrateModules(moduleRepository.findByCourseIdOrderByModuleOrderAsc(courseId));
    }

    @PutMapping("/modules/{id}")
    public ResponseEntity<Module> update(
            @PathVariable Long id,
            @RequestBody Module moduleDetails,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return moduleRepository.findById(id).map(module -> {
            accessControlService.requireOwnership(module.getCourse(), userId, role);
            module.setTitle(moduleDetails.getTitle());
            module.setDescription(moduleDetails.getDescription());
            if (moduleDetails.getIsPublished() != null) {
                module.setIsPublished(moduleDetails.getIsPublished());
            }
            if (moduleDetails.getIsLocked() != null) {
                module.setIsLocked(moduleDetails.getIsLocked());
            }
            if (moduleDetails.getVideoUrl() != null || moduleDetails.getNotes() != null || moduleDetails.getDuration() != null || moduleDetails.getModuleResources() != null) {
                module.setVideoUrl(moduleDetails.getVideoUrl());
                module.setNotes(moduleDetails.getNotes());
                module.setDuration(moduleDetails.getDuration());
                if (moduleDetails.getModuleResources() != null) {
                    module.setModuleResources(moduleDetails.getModuleResources());
                }
            }
            normalizeModule(module);
            return ResponseEntity.ok(hydrateModule(moduleRepository.save(module)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/modules/{id}/publish")
    public ResponseEntity<Module> publishModule(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean published,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return moduleRepository.findById(id).map(module -> {
            accessControlService.requireOwnership(module.getCourse(), userId, role);
            module.setIsPublished(published);
            return ResponseEntity.ok(hydrateModule(moduleRepository.save(module)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/modules/{id}/lock")
    public ResponseEntity<Module> lockModule(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean locked,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return moduleRepository.findById(id).map(module -> {
            accessControlService.requireOwnership(module.getCourse(), userId, role);
            module.setIsLocked(locked);
            return ResponseEntity.ok(hydrateModule(moduleRepository.save(module)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/modules/{id}")
    public ResponseEntity<Object> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return moduleRepository.findById(id).map(module -> {
            accessControlService.requireOwnership(module.getCourse(), userId, role);
            moduleRepository.delete(module);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/modules/{id}/resources")
    public ResponseEntity<List<LessonResource>> getResources(@PathVariable Long id) {
        return moduleRepository.findById(id)
                .map(module -> ResponseEntity.ok(getOrCreatePrimaryLesson(module).getResources()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/modules/{id}/resources")
    public ResponseEntity<LessonResource> addResource(
            @PathVariable Long id,
            @RequestBody LessonResource resource,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return moduleRepository.findById(id).map(module -> {
            accessControlService.requireOwnership(module.getCourse(), userId, role);
            Lesson lesson = getOrCreatePrimaryLesson(module);
            if (resource.getDisplayOrder() == null) {
                resource.setDisplayOrder(lesson.getResources().size() + 1);
            }
            if (resource.getResourceType() == null || resource.getResourceType().isBlank()) {
                resource.setResourceType("LINK");
            }
            lesson.addResource(resource);
            moduleRepository.save(module);
            LessonResource saved = resourceRepository.save(resource);
            publishCourseContentAdded(module.getCourse(), "resource", saved.getTitle(), saved.getId());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/modules/{id}/resources/upload")
    public ResponseEntity<LessonResource> uploadResource(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return moduleRepository.findById(id).map(module -> {
            accessControlService.requireOwnership(module.getCourse(), userId, role);
            Lesson lesson = getOrCreatePrimaryLesson(module);

            String fileName = fileStorageService.storeResourceFile(file);
            LessonResource resource = new LessonResource();
            resource.setTitle(title != null && !title.isBlank() ? title : file.getOriginalFilename());
            resource.setResourceType(resourceType != null && !resourceType.isBlank() ? resourceType : detectResourceType(file.getOriginalFilename()));
            resource.setResourceUrl("/courses/uploads/" + fileName);
            resource.setFileSize(formatFileSize(file.getSize()));
            resource.setDisplayOrder(lesson.getResources().size() + 1);
            lesson.addResource(resource);
            moduleRepository.save(module);
            LessonResource saved = resourceRepository.save(resource);
            publishCourseContentAdded(module.getCourse(), "resource", saved.getTitle(), saved.getId());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/modules/{moduleId}/resources/{resourceId}")
    public ResponseEntity<Object> deleteResource(
            @PathVariable Long moduleId,
            @PathVariable Long resourceId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        return moduleRepository.findById(moduleId).map(module -> {
            accessControlService.requireOwnership(module.getCourse(), userId, role);
            resourceRepository.deleteById(resourceId);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/courses/{courseId}/modules/reorder")
    public ResponseEntity<List<Module>> reorderModules(
            @PathVariable Long courseId,
            @RequestBody Map<String, List<Long>> body,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role
    ) {
        List<Long> requestedIds = body.get("ids");
        if (requestedIds == null || requestedIds.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        return courseRepository.findById(courseId).map(course -> {
            accessControlService.requireOwnership(course, userId, role);

            List<Module> currentModules = moduleRepository.findByCourseIdOrderByModuleOrderAsc(courseId);
            Map<Long, Module> modulesById = new HashMap<>();
            for (Module module : currentModules) {
                modulesById.put(module.getId(), module);
            }

            Set<Long> uniqueRequestedIds = new LinkedHashSet<>(requestedIds);
            for (Long moduleId : uniqueRequestedIds) {
                if (!modulesById.containsKey(moduleId)) {
                    return ResponseEntity.badRequest().<List<Module>>build();
                }
            }

            List<Module> reordered = new ArrayList<>();
            for (Long moduleId : uniqueRequestedIds) {
                reordered.add(modulesById.get(moduleId));
            }
            for (Module module : currentModules) {
                if (!uniqueRequestedIds.contains(module.getId())) {
                    reordered.add(module);
                }
            }

            for (int i = 0; i < reordered.size(); i++) {
                reordered.get(i).setModuleOrder(i + 1);
                normalizeModule(reordered.get(i));
            }

            return ResponseEntity.ok(hydrateModules(moduleRepository.saveAll(reordered)));
        }).orElse(ResponseEntity.notFound().build());
    }

    private Module hydrateModule(Module module) {
        if (module == null) {
            return null;
        }
        List<Module> hydrated = hydrateModules(List.of(module));
        return hydrated.isEmpty() ? module : hydrated.get(0);
    }

    private List<Module> hydrateModules(List<Module> modules) {
        if (modules == null || modules.isEmpty()) {
            return modules;
        }

        Map<Long, Module> modulesById = new LinkedHashMap<>();
        for (Module module : modules) {
            if (module != null && module.getId() != null) {
                entityManager.detach(module); // Detach module
                module.setLessons(new ArrayList<>());
                modulesById.put(module.getId(), module);
            }
        }
        if (modulesById.isEmpty()) {
            return modules;
        }

        List<Lesson> lessons = lessonRepository.findByModuleIdInOrderByModuleIdAscLessonOrderAsc(new ArrayList<>(modulesById.keySet()));
        Map<Long, List<Lesson>> lessonsByModuleId = new LinkedHashMap<>();
        for (Lesson lesson : lessons) {
            entityManager.detach(lesson); // Detach lesson
            lesson.setResourcesForHydration(new ArrayList<>());
            lessonsByModuleId.computeIfAbsent(lesson.getModule().getId(), ignored -> new ArrayList<>()).add(lesson);
        }

        List<Long> lessonIds = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (lesson.getId() != null) {
                lessonIds.add(lesson.getId());
            }
        }

        Map<Long, List<LessonResource>> resourcesByLessonId = new LinkedHashMap<>();
        if (!lessonIds.isEmpty()) {
            for (LessonResource resource : resourceRepository.findByLessonIdInOrderByLessonIdAscDisplayOrderAsc(lessonIds)) {
                entityManager.detach(resource); // Detach resource
                resourcesByLessonId.computeIfAbsent(resource.getLesson().getId(), ignored -> new ArrayList<>()).add(resource);
            }
        }

        for (Lesson lesson : lessons) {
            lesson.setResourcesForHydration(new ArrayList<>(resourcesByLessonId.getOrDefault(lesson.getId(), List.of())));
        }

        for (Module module : modulesById.values()) {
            List<Lesson> moduleLessons = new ArrayList<>(lessonsByModuleId.getOrDefault(module.getId(), List.of()));
            for (Lesson lesson : moduleLessons) {
                lesson.setModule(module);
            }
            module.setLessons(moduleLessons);
        }

        return modules;
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
            LOGGER.warn("Error publishing course content event for courseId={} contentType={}", course.getId(), contentType, e);
        }
    }

    private void normalizeModule(Module module) {
        Lesson lesson = getOrCreatePrimaryLesson(module);
        if (module.getIsPublished() == null) {
            module.setIsPublished(Boolean.TRUE);
        }
        if (module.getIsLocked() == null) {
            module.setIsLocked(Boolean.FALSE);
        }
        lesson.setModule(module);
        lesson.setLessonOrder(1);
        lesson.setIsFreePreview(Boolean.FALSE);
        lesson.setTitle(module.getTitle() != null && !module.getTitle().isBlank() ? module.getTitle() : "Module content");

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
            resource.setDisplayOrder(displayOrder++);
            lesson.addResource(resource);
        }
    }

    private Lesson getOrCreatePrimaryLesson(Module module) {
        Lesson lesson = module.getOrCreatePrimaryLesson();
        lesson.setModule(module);
        return lesson;
    }

    private String detectResourceType(String fileName) {
        if (fileName == null) return "OTHER";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "DOC";
        if (lower.endsWith(".zip")) return "ZIP";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "PPT";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) return "SPREADSHEET";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif")) return "IMAGE";
        return "OTHER";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
