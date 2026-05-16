package com.olp.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads");
        String uploadPath = uploadDir.toFile().getAbsolutePath();
        // Only avatars are served as static assets. Verification documents are
        // returned through role-checked controller endpoints.
        registry.addResourceHandler("/users/uploads/avatars/**")
                .addResourceLocations("file:" + uploadPath + "/avatars/");
    }
}
