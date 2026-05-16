package com.olp.course.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService() {
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public String storeFile(MultipartFile file) {
        validateFile(file, null, "File");
        return copyFile(file);
    }

    public String storeImage(MultipartFile file) {
        validateFile(file, List.of(".jpg", ".jpeg", ".png", ".webp", ".gif"), "Image");
        return copyFile(file);
    }

    public String storeResourceFile(MultipartFile file) {
        validateFile(file, List.of(
                ".pdf", ".doc", ".docx", ".zip", ".ppt", ".pptx", ".xls", ".xlsx",
                ".png", ".jpg", ".jpeg", ".webp", ".gif", ".txt", ".csv"
        ), "Resource");
        return copyFile(file);
    }

    private String copyFile(MultipartFile file) {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = "";
        try {
            if (originalFileName != null && originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            }
        } catch(Exception e) {
            fileExtension = "";
        }
        
        String fileName = UUID.randomUUID().toString() + fileExtension;

        try {
            if (fileName.contains("..")) {
                throw new RuntimeException("Sorry! Filename contains invalid path sequence " + fileName);
            }
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    private void validateFile(MultipartFile file, List<String> allowedExtensions, String label) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException(label + " upload cannot be empty.");
        }

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new RuntimeException(label + " filename is invalid.");
        }

        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return;
        }

        String lowerName = originalFileName.toLowerCase(Locale.ROOT);
        boolean allowed = allowedExtensions.stream().anyMatch(lowerName::endsWith);
        if (!allowed) {
            throw new RuntimeException(label + " type is not supported.");
        }
    }
}
