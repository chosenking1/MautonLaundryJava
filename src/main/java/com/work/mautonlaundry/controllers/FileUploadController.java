package com.work.mautonlaundry.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/files")
@PreAuthorize("hasRole('ADMIN')")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8079}")
    private String baseUrl;

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadResponse> uploadImage(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        try {
            log.info("Uploading image: originalName={}, size={} bytes", file.getOriginalFilename(), file.getSize());

            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null) {
                int lastDot = originalFilename.lastIndexOf(".");
                if (lastDot >= 0) {
                    extension = originalFilename.substring(lastDot);
                }
            }
            String filename = UUID.randomUUID().toString() + extension;
            
            // Save file
            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Image uploaded successfully: {}", filePath);

            String relativePath = "/" + uploadDir + "/" + filename;
            String publicUrl = baseUrl + "/api/v1/images/" + filename;
            return ResponseEntity.ok(new UploadResponse(filename, relativePath, publicUrl));
        } catch (IOException e) {
            log.error("Failed to upload image", e);
            throw new IllegalStateException("Failed to upload file");
        }
    }

    public static class UploadResponse {
        public String filename;
        public String path;
        public String url;

        public UploadResponse(String filename, String path, String url) {
            this.filename = filename;
            this.path = path;
            this.url = url;
        }
    }
}
