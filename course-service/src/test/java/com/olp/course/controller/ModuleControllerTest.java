package com.olp.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.course.model.Course;
import com.olp.course.model.Lesson;
import com.olp.course.model.LessonResource;
import com.olp.course.model.Module;
import com.olp.course.repository.CourseRepository;
import com.olp.course.repository.LessonRepository;
import com.olp.course.repository.LessonResourceRepository;
import com.olp.course.repository.ModuleRepository;
import com.olp.course.service.AccessControlService;
import com.olp.course.service.FileStorageService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleControllerTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private LessonResourceRepository resourceRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private EntityManager entityManager;

    private ModuleController moduleController;

    @BeforeEach
    void setUp() {
        moduleController = new ModuleController(
                moduleRepository,
                courseRepository,
                lessonRepository,
                resourceRepository,
                accessControlService,
                fileStorageService,
                kafkaTemplate,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(moduleController, "entityManager", entityManager);
    }

    @Test
    void createAssignsDefaultOrderNormalizesLessonAndPublishesEvent() {
        Course course = new Course();
        course.setId(10L);
        course.setTitle("Java Fundamentals");
        course.setInstructorId(5L);

        Module module = new Module();
        module.setTitle("Introduction");
        LessonResource validResource = new LessonResource();
        validResource.setTitle("Slides");
        validResource.setResourceUrl("/slides");
        LessonResource blankResource = new LessonResource();
        module.setModuleResources(List.of(validResource, blankResource));

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(moduleRepository.save(any(Module.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<Module> response = moduleController.create(10L, module, 5L, "INSTRUCTOR");

        assertEquals(200, response.getStatusCode().value());
        Module created = response.getBody();
        assertEquals(1, created.getModuleOrder());
        Lesson primaryLesson = created.getLessons().get(0);
        assertEquals("Introduction", primaryLesson.getTitle());
        assertEquals(Boolean.FALSE, primaryLesson.getIsFreePreview());
        assertEquals(1, primaryLesson.getResources().size());
        assertEquals("LINK", primaryLesson.getResources().get(0).getResourceType());
        verify(kafkaTemplate).send(eq("course-events"), contains("\"contentType\":\"module\""));
    }

    @Test
    void addResourceDefaultsDisplayOrderAndType() {
        Course course = new Course();
        course.setInstructorId(5L);
        Module module = new Module();
        module.setId(4L);
        module.setCourse(course);
        Lesson primaryLesson = module.getOrCreatePrimaryLesson();
        LessonResource existing = new LessonResource();
        existing.setTitle("Existing");
        existing.setResourceUrl("/existing");
        primaryLesson.addResource(existing);

        LessonResource resource = new LessonResource();
        resource.setTitle("Reference link");
        resource.setResourceUrl("https://example.com");

        when(moduleRepository.findById(4L)).thenReturn(Optional.of(module));
        when(resourceRepository.save(any(LessonResource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<LessonResource> response = moduleController.addResource(4L, resource, 5L, "INSTRUCTOR");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().getDisplayOrder());
        assertEquals("LINK", response.getBody().getResourceType());
        assertSame(primaryLesson, response.getBody().getLesson());
    }

    @Test
    void uploadResourceDerivesMetadataFromFile() {
        Course course = new Course();
        course.setInstructorId(5L);
        Module module = new Module();
        module.setId(4L);
        module.setCourse(course);
        module.getOrCreatePrimaryLesson();
        MockMultipartFile file = new MockMultipartFile("file", "outline.pdf", "application/pdf", "hello".getBytes());

        when(moduleRepository.findById(4L)).thenReturn(Optional.of(module));
        when(fileStorageService.storeResourceFile(file)).thenReturn("stored-outline.pdf");
        when(resourceRepository.save(any(LessonResource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<LessonResource> response = moduleController.uploadResource(4L, file, null, null, 5L, "INSTRUCTOR");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("outline.pdf", response.getBody().getTitle());
        assertEquals("PDF", response.getBody().getResourceType());
        assertEquals("/courses/uploads/stored-outline.pdf", response.getBody().getResourceUrl());
        assertNotNull(response.getBody().getFileSize());
    }

    @Test
    void reorderModulesAppendsOmittedModulesAtTheEnd() {
        Course course = new Course();
        course.setId(6L);
        Module first = module(11L, 1, course);
        Module second = module(12L, 2, course);
        Module third = module(13L, 3, course);

        when(courseRepository.findById(6L)).thenReturn(Optional.of(course));
        when(moduleRepository.findByCourseIdOrderByModuleOrderAsc(6L)).thenReturn(List.of(first, second, third));
        when(moduleRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonRepository.findByModuleIdInOrderByModuleIdAscLessonOrderAsc(List.of(13L, 11L, 12L))).thenReturn(List.of());

        ResponseEntity<List<Module>> response = moduleController.reorderModules(
                6L,
                Map.of("ids", List.of(13L, 11L)),
                5L,
                "INSTRUCTOR"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals(List.of(13L, 11L, 12L), response.getBody().stream().map(Module::getId).toList());
        assertEquals(1, third.getModuleOrder());
        assertEquals(2, first.getModuleOrder());
        assertEquals(3, second.getModuleOrder());
    }

    @Test
    void reorderModulesRejectsUnknownIds() {
        Course course = new Course();
        course.setId(6L);
        when(courseRepository.findById(6L)).thenReturn(Optional.of(course));
        when(moduleRepository.findByCourseIdOrderByModuleOrderAsc(6L)).thenReturn(List.of(module(11L, 1, course)));

        ResponseEntity<List<Module>> response = moduleController.reorderModules(
                6L,
                Map.of("ids", List.of(99L)),
                5L,
                "INSTRUCTOR"
        );

        assertEquals(400, response.getStatusCode().value());
    }

    private Module module(Long id, Integer order, Course course) {
        Module module = new Module();
        module.setId(id);
        module.setModuleOrder(order);
        module.setCourse(course);
        return module;
    }
}
