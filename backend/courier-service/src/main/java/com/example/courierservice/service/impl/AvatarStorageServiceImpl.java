package com.example.courierservice.service.impl;

import com.example.courierservice.exception.InvalidAvatarException;
import com.example.courierservice.service.AvatarStorageService;
import com.example.courierservice.service.StoredAvatarResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
@Service
public class AvatarStorageServiceImpl implements AvatarStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final Path storageDir;

    public AvatarStorageServiceImpl(@Value("${courier.avatar.storage-path:uploads/avatars}") String storagePath) {
        this.storageDir = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not initialize avatar storage directory: " + storageDir, e);
        }
    }

    @Override
    public String store(Long courierId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidAvatarException("Avatar file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidAvatarException("Avatar file exceeds the maximum allowed size of 5MB");
        }

        AvatarImageType imageType = detectImageType(file);
        deleteExisting(courierId);

        Path target = storageDir.resolve("avatar-" + courierId + "." + imageType.extension());
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store avatar for courier " + courierId, e);
        }

        log.info("Stored avatar for courier {} at {}", courierId, target);
        return "/courier/" + courierId + "/avatar";
    }

    @Override
    public Optional<StoredAvatarResource> load(Long courierId) {
        Path match = findExisting(courierId);
        if (match == null) {
            return Optional.empty();
        }
        AvatarImageType imageType = AvatarImageType.byExtension(extensionOf(match));
        return Optional.of(new StoredAvatarResource(new FileSystemResource(match), imageType.mediaType()));
    }

    private void deleteExisting(Long courierId) {
        Path existing = findExisting(courierId);
        if (existing != null) {
            try {
                Files.deleteIfExists(existing);
            } catch (IOException e) {
                log.warn("Could not delete previous avatar {} for courier {}", existing, courierId, e);
            }
        }
    }

    private Path findExisting(Long courierId) {
        String glob = "avatar-" + courierId + ".*";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, glob)) {
            for (Path path : stream) {
                return path;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list avatar storage directory", e);
        }
        return null;
    }

    private String extensionOf(Path path) {
        String name = path.getFileName().toString();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private AvatarImageType detectImageType(MultipartFile file) {
        byte[] header;
        try {
            header = file.getInputStream().readNBytes(12);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded avatar", e);
        }
        return AvatarImageType.detect(header)
                .orElseThrow(() -> new InvalidAvatarException(
                        "Unsupported file type. Allowed formats: JPG, JPEG, PNG, WEBP"));
    }
}
