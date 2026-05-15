package com.olp.course.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "lesson_resources")
public class LessonResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    /** VIDEO, YOUTUBE, PDF, PPT, DOC, IMAGE, ZIP, RICH_TEXT, EMBED, LINK, OTHER */
    private String resourceType;

    private String resourceUrl;
    @Column(columnDefinition = "TEXT")
    private String content;
    private String mimeType;
    private String fileSize;
    private Integer displayOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id")
    @JsonIgnore
    private Lesson lesson;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceUrl() { return resourceUrl; }
    public void setResourceUrl(String resourceUrl) { this.resourceUrl = resourceUrl; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getFileSize() { return fileSize; }
    public void setFileSize(String fileSize) { this.fileSize = fileSize; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public Lesson getLesson() { return lesson; }
    public void setLesson(Lesson lesson) { this.lesson = lesson; }
}
