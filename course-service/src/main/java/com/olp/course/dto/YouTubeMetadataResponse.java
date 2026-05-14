package com.olp.course.dto;

import java.time.LocalDateTime;
import java.util.List;

public class YouTubeMetadataResponse {
    private String videoId;
    private String url;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String channelName;
    private String duration;
    private List<String> tags;
    private String seoSummary;
    private LocalDateTime fetchedAt;

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getSeoSummary() { return seoSummary; }
    public void setSeoSummary(String seoSummary) { this.seoSummary = seoSummary; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
