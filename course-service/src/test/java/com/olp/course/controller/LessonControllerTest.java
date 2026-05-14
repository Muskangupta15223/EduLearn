package com.olp.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.course.model.Course;
import com.olp.course.model.Lesson;
import com.olp.course.repository.LessonRepository;
import com.olp.course.repository.LessonResourceRepository;
import com.olp.course.repository.ModuleRepository;
import com.olp.course.service.AccessControlService;
import com.olp.course.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonControllerTest {

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private LessonResourceRepository resourceRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private LessonController lessonController;

    @BeforeEach
    void setUp() {
        lessonController = new LessonController(
                lessonRepository,
                moduleRepository,
                resourceRepository,
                accessControlService,
                fileStorageService,
                kafkaTemplate,
                new ObjectMapper()
        );
    }

    @Test
    void reorderLessonsUpdatesLessonOrderAndKeepsOmittedLessonsLast() {
        Course course = new Course();
        course.setId(9L);

        com.olp.course.model.Module module = new com.olp.course.model.Module();
        module.setId(4L);
        module.setCourse(course);

        Lesson first = lesson(11L, 1);
        Lesson second = lesson(12L, 2);
        Lesson third = lesson(13L, 3);

        when(moduleRepository.findById(4L)).thenReturn(Optional.of(module));
        when(lessonRepository.findByModuleIdOrderByLessonOrderAsc(4L))
                .thenReturn(List.of(first, second, third));
        when(lessonRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<List<Lesson>> response = lessonController.reorderLessons(
                4L,
                Map.of("lessonIds", List.of(13L, 11L)),
                7L,
                "INSTRUCTOR"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals(List.of(13L, 11L, 12L), response.getBody().stream().map(Lesson::getId).toList());
        assertEquals(1, third.getLessonOrder());
        assertEquals(2, first.getLessonOrder());
        assertEquals(3, second.getLessonOrder());
        verify(accessControlService).requireOwnership(course, 7L, "INSTRUCTOR");
        verify(lessonRepository).saveAll(List.of(third, first, second));
    }

    @Test
    void reorderLessonsRejectsUnknownLessonId() {
        Course course = new Course();
        com.olp.course.model.Module module = new com.olp.course.model.Module();
        module.setCourse(course);

        when(moduleRepository.findById(4L)).thenReturn(Optional.of(module));
        when(lessonRepository.findByModuleIdOrderByLessonOrderAsc(4L))
                .thenReturn(List.of(lesson(11L, 1)));

        ResponseEntity<List<Lesson>> response = lessonController.reorderLessons(
                4L,
                Map.of("lessonIds", List.of(99L)),
                7L,
                "INSTRUCTOR"
        );

        assertEquals(400, response.getStatusCode().value());
    }

    private Lesson lesson(Long id, Integer order) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setLessonOrder(order);
        return lesson;
    }
}
