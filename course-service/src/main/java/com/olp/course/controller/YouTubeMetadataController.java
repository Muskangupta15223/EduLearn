package com.olp.course.controller;

import com.olp.course.dto.YouTubeMetadataResponse;
import com.olp.course.service.YouTubeMetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/courses/youtube")
public class YouTubeMetadataController {

    private final YouTubeMetadataService youTubeMetadataService;

    public YouTubeMetadataController(YouTubeMetadataService youTubeMetadataService) {
        this.youTubeMetadataService = youTubeMetadataService;
    }

    @GetMapping("/metadata")
    public ResponseEntity<YouTubeMetadataResponse> getMetadata(@RequestParam("url") String url) {
        return ResponseEntity.ok(youTubeMetadataService.fetchMetadata(url));
    }
}
