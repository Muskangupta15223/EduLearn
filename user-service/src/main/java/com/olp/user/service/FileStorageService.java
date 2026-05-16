package com.olp.user.service;

import java.io.IOException;
import java.nio.file.Files;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

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

    public String storeAvatar(MultipartFile file) {
        validateFile(file, Set.of("image/jpeg", "image/png", "image/webp"));
        return storeFile(file, "avatars");
    }

    public String storeInstructorVerificationFile(MultipartFile file) {
        validateFile(file, Set.of("image/jpeg", "image/png", "image/webp", "application/pdf"));
        return storeFile(file, "verification");
    }

    public Resource loadVerificationFile(String storedPath) {
        try {
            if (storedPath == null || storedPath.isBlank()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification document not found");
            }

            String relativePath = storedPath.replace("/users/uploads/", "");
            if (!relativePath.startsWith("verification/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification document path");
            }

            Path filePath = this.fileStorageLocation.resolve(relativePath).normalize();
            if (!filePath.startsWith(this.fileStorageLocation.resolve("verification").normalize())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification document path");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification document not found");
            }
            return resource;
        } catch (java.net.MalformedURLException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification document path");
        }
    }

    private String storeFile(MultipartFile file, String subdirectory) {
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = "";
        try {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        } catch(Exception e) {
            fileExtension = "";
        }
        
        String fileName = UUID.randomUUID().toString() + fileExtension;

        try {
            if (fileName.contains("..")) {
                throw new RuntimeException("Sorry! Filename contains invalid path sequence " + fileName);
            }
            Path targetDirectory = this.fileStorageLocation.resolve(subdirectory);
            Files.createDirectories(targetDirectory);
            Path targetLocation = targetDirectory.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return subdirectory + "/" + fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    private void validateFile(MultipartFile file, Set<String> allowedContentTypes) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type");
        }
    }
}
