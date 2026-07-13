package com.example.authservice.service.impl;

import com.example.authservice.exception.InvalidAvatarFileException;
import com.example.authservice.service.AvatarStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Filenames are always server-generated (UUID + an extension derived from the validated
 * content type) rather than taken from the client-supplied original filename - avoids
 * trusting client input for anything that becomes a filesystem path.
 */
@Slf4j
@Service
public class AvatarStorageServiceImpl implements AvatarStorageService {

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png"
    );

    private static final String URL_PREFIX = "/auth/avatars/";

    private final Path uploadDir;

    public AvatarStorageServiceImpl(@Value("${app.avatar.upload-dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create avatar upload directory: " + this.uploadDir, e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        String extension = ALLOWED_CONTENT_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new InvalidAvatarFileException(
                    "Only JPG and PNG images are allowed (received: " + file.getContentType() + ")");
        }

        String filename = UUID.randomUUID() + extension;
        Path target = uploadDir.resolve(filename);

        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store avatar file", e);
        }

        return URL_PREFIX + filename;
    }

    @Override
    public void delete(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(URL_PREFIX)) {
            return;
        }

        Path target = uploadDir.resolve(avatarUrl.substring(URL_PREFIX.length()));
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete old avatar file {}: {}", target, e.getMessage());
        }
    }
}