package com.olp.course.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "modules")
public class Module {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer moduleOrder;
    private Boolean isPublished = true;
    private Boolean isLocked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    @JsonIgnore
    private Course course;

    @OneToMany(mappedBy = "module", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Lesson> lessons = new ArrayList<>();

    @Transient
    @JsonProperty("videoUrl")
    public String getVideoUrl() {
        return getPrimaryLesson().getVideoUrl();
    }

    @JsonProperty("videoUrl")
    public void setVideoUrl(String videoUrl) {
        getOrCreatePrimaryLesson().setVideoUrl(videoUrl);
    }

    @Transient
    @JsonProperty("notes")
    public String getNotes() {
        return getPrimaryLesson().getContent();
    }

    @JsonProperty("notes")
    public void setNotes(String notes) {
        getOrCreatePrimaryLesson().setContent(notes);
    }

    @Transient
    @JsonProperty("duration")
    public String getDuration() {
        return getPrimaryLesson().getDuration();
    }

    @JsonProperty("duration")
    public void setDuration(String duration) {
        getOrCreatePrimaryLesson().setDuration(duration);
    }

    @Transient
    @JsonProperty("contentLessonId")
    public Long getContentLessonId() {
        return getPrimaryLesson().getId();
    }

    @Transient
    @JsonProperty("resources")
    public List<LessonResource> getModuleResources() {
        return getPrimaryLesson().getResources();
    }

    @JsonProperty("resources")
    public void setModuleResources(List<LessonResource> resources) {
        getOrCreatePrimaryLesson().setResources(resources);
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getModuleOrder() { return moduleOrder; }
    public void setModuleOrder(Integer moduleOrder) { this.moduleOrder = moduleOrder; }
    public Boolean getIsPublished() { return isPublished; }
    public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }
    public Boolean getIsLocked() { return isLocked; }
    public void setIsLocked(Boolean isLocked) { this.isLocked = isLocked; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public List<Lesson> getLessons() { return lessons; }
    public void setLessons(List<Lesson> lessons) { this.lessons = lessons; }

    public Lesson getPrimaryLesson() {
        return lessons == null || lessons.isEmpty() ? new Lesson() : lessons.get(0);
    }

    public Lesson getOrCreatePrimaryLesson() {
        if (lessons == null) {
            lessons = new ArrayList<>();
        }
        if (lessons.isEmpty()) {
            Lesson lesson = new Lesson();
            lesson.setLessonOrder(1);
            lesson.setIsFreePreview(Boolean.FALSE);
            lesson.setTitle(title != null && !title.isBlank() ? title : "Module content");
            lesson.setModule(this);
            lessons.add(lesson);
        }
        return lessons.get(0);
    }
}
