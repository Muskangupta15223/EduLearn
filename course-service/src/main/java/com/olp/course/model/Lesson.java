package com.olp.course.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String videoUrl;
    private String duration;
    @com.fasterxml.jackson.annotation.JsonProperty("isFreePreview")
    private Boolean isFreePreview;
    private Boolean isLocked = false;
    private Integer lessonOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    @JsonIgnore
    private Module module;

    @OneToMany(mappedBy = "lesson", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LessonResource> resources = new ArrayList<>();

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public Boolean getIsFreePreview() { return isFreePreview; }
    public void setIsFreePreview(Boolean isFreePreview) { this.isFreePreview = isFreePreview; }
    public Boolean getIsLocked() { return isLocked; }
    public void setIsLocked(Boolean isLocked) { this.isLocked = isLocked; }
    public Integer getLessonOrder() { return lessonOrder; }
    public void setLessonOrder(Integer lessonOrder) { this.lessonOrder = lessonOrder; }
    public Module getModule() { return module; }
    public void setModule(Module module) { this.module = module; }
    public List<LessonResource> getResources() { return resources; }
    public void setResources(List<LessonResource> resources) {
        this.resources.clear();
        if (resources != null) {
            for (LessonResource resource : resources) {
                addResource(resource);
            }
        }
    }

    public void setResourcesForHydration(List<LessonResource> resources) {
        this.resources = resources;
    }

    public void addResource(LessonResource resource) {
        if (resource == null) {
            return;
        }
        resource.setLesson(this);
        this.resources.add(resource);
    }
}
