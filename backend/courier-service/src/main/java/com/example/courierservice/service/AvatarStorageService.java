package com.example.courierservice.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * Persists courier avatar images on disk (a Docker-mounted volume in production,
 * see docker-compose.yml) and serves them back. Pure file I/O and format validation -
 * ownership/authorization is handled by the caller (see CourierAssignmentService).
 */
public interface AvatarStorageService {

    /**
     * Validates and stores the given file as the avatar for the given courier,
     * replacing any previously stored avatar for that courier.
     *
     * @return the URL clients should use to retrieve the stored avatar
     * @throws com.example.courierservice.exception.InvalidAvatarException if the file is missing,
     *         empty, exceeds the maximum allowed size, or is not a supported image type
     */
    String store(Long courierId, MultipartFile file);

    /**
     * Loads the previously stored avatar for the given courier, if any.
     */
    Optional<StoredAvatarResource> load(Long courierId);
}