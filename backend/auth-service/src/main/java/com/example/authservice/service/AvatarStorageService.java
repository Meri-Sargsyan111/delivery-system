package com.example.authservice.service;

import org.springframework.web.multipart.MultipartFile;

public interface AvatarStorageService {

    /**
     * Validates and saves the uploaded file, returning the URL it will be servable at.
     *
     * @throws com.example.authservice.exception.InvalidAvatarFileException if the file's
     *         content type isn't one of the allowed image types.
     */
    String store(MultipartFile file);

    /**
     * Best-effort delete of a previously stored avatar. Safe to call with {@code null}
     * (first-ever upload) or a URL this service didn't generate.
     */
    void delete(String avatarUrl);
}