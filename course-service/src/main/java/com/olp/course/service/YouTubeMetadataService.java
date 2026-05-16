package com.olp.course.service;

import com.olp.course.dto.YouTubeMetadataResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class YouTubeMetadataService {

    private static final Pattern YOUTUBE_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    private final Map<String, YouTubeMetadataResponse> cache = new ConcurrentHashMap<>();
    private final RestTemplate externalRestTemplate = new RestTemplate();

    @Value("${youtube.api-key:}")
    private String apiKey;

    public YouTubeMetadataResponse fetchMetadata(String rawUrl) {
        String videoId = extractVideoId(rawUrl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid YouTube URL"));
        return cache.computeIfAbsent(videoId, id -> fetchFresh(id, rawUrl));
    }

    public Optional<String> extractVideoId(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = YOUTUBE_ID_PATTERN.matcher(rawUrl.trim());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private YouTubeMetadataResponse fetchFresh(String videoId, String rawUrl) {
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                return fetchViaDataApi(videoId, rawUrl);
            } catch (RuntimeException ignored) {
                // Fall through to oEmbed. In production this should be logged through a central logger.
            }
        }
        return fetchViaOEmbed(videoId, rawUrl);
    }

    private YouTubeMetadataResponse fetchViaDataApi(String videoId, String rawUrl) {
        String endpoint = "https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails&id="
                + videoId + "&key=" + apiKey;
        Map<?, ?> body = externalRestTemplate.getForObject(endpoint, Map.class);
        Object itemsObj = body == null ? null : body.get("items");
        List<?> items = itemsObj instanceof List<?> list ? list : List.of();
        if (items.isEmpty() || !(items.get(0) instanceof Map<?, ?> item)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "YouTube video not found");
        }

        Map<?, ?> snippet = item.get("snippet") instanceof Map<?, ?> snippetMap ? snippetMap : Map.of();
        Map<?, ?> contentDetails = item.get("contentDetails") instanceof Map<?, ?> detailsMap ? detailsMap : Map.of();
        Map<?, ?> thumbnails = snippet.get("thumbnails") instanceof Map<?, ?> thumbnailsMap ? thumbnailsMap : Map.of();
        Map<?, ?> high = thumbnails.get("high") instanceof Map<?, ?> highMap ? highMap : Map.of();

        YouTubeMetadataResponse response = baseResponse(videoId, rawUrl);
        response.setTitle(stringValue(snippet.get("title")));
        response.setDescription(stringValue(snippet.get("description")));
        response.setChannelName(stringValue(snippet.get("channelTitle")));
        response.setDuration(stringValue(contentDetails.get("duration")));
        response.setThumbnailUrl(stringValue(high.get("url")));
        Object tagsObj = snippet.get("tags");
        if (tagsObj instanceof List<?> tags) {
            response.setTags(tags.stream().map(String::valueOf).limit(12).toList());
        }
        response.setSeoSummary(buildSeoSummary(response));
        return response;
    }

    private YouTubeMetadataResponse fetchViaOEmbed(String videoId, String rawUrl) {
        YouTubeMetadataResponse response = baseResponse(videoId, rawUrl);
        try {
            String encodedUrl = URLEncoder.encode("https://www.youtube.com/watch?v=" + videoId, StandardCharsets.UTF_8);
            URI endpoint = URI.create("https://www.youtube.com/oembed?url=" + encodedUrl + "&format=json");
            Map<?, ?> body = externalRestTemplate.getForObject(endpoint, Map.class);
            if (body != null) {
                response.setTitle(stringValue(body.get("title")));
                response.setChannelName(stringValue(body.get("author_name")));
                response.setThumbnailUrl(stringValue(body.get("thumbnail_url")));
            }
        } catch (RuntimeException ignored) {
            response.setTitle("YouTube Video " + videoId);
        }
        if (response.getThumbnailUrl() == null || response.getThumbnailUrl().isBlank()) {
            response.setThumbnailUrl("https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg");
        }
        response.setDescription("");
        response.setDuration("");
        response.setTags(deriveTags(response.getTitle()));
        response.setSeoSummary(buildSeoSummary(response));
        return response;
    }

    private YouTubeMetadataResponse baseResponse(String videoId, String rawUrl) {
        YouTubeMetadataResponse response = new YouTubeMetadataResponse();
        response.setVideoId(videoId);
        response.setUrl(rawUrl);
        response.setFetchedAt(LocalDateTime.now());
        response.setTags(List.of());
        return response;
    }

    private List<String> deriveTags(String title) {
        if (title == null || title.isBlank()) {
            return List.of("online learning", "video lesson");
        }
        return Arrays.stream(title.toLowerCase().replaceAll("[^a-z0-9 ]", " ").split("\\s+"))
                .filter(token -> token.length() > 3)
                .distinct()
                .limit(8)
                .toList();
    }

    private String buildSeoSummary(YouTubeMetadataResponse response) {
        String title = response.getTitle() == null || response.getTitle().isBlank() ? "this video lesson" : response.getTitle();
        String channel = response.getChannelName() == null || response.getChannelName().isBlank() ? "the instructor" : response.getChannelName();
        return "Learn with " + title + " by " + channel + ". Includes guided video content, course resources, and structured LMS progress tracking.";
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
