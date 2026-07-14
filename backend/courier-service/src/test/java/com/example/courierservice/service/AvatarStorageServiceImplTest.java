package com.example.courierservice.service;

import com.example.courierservice.exception.InvalidAvatarException;
import com.example.courierservice.service.impl.AvatarStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarStorageServiceImplTest {

    private static final byte[] JPEG_BYTES =
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] PNG_BYTES =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] WEBP_BYTES =
            {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @TempDir
    Path tempDir;

    private AvatarStorageServiceImpl avatarStorageService;

    @BeforeEach
    void setUp() {
        avatarStorageService = new AvatarStorageServiceImpl(tempDir.toString());
    }

    @Test
    void store_withValidJpeg_succeedsAndReturnsAvatarUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", JPEG_BYTES);

        String url = avatarStorageService.store(1L, file);

        assertThat(url).isEqualTo("/courier/1/avatar");
    }

    @Test
    void store_withValidPng_succeeds() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", PNG_BYTES);

        String url = avatarStorageService.store(2L, file);

        assertThat(url).isEqualTo("/courier/2/avatar");
    }

    @Test
    void store_withValidWebp_succeeds() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.webp", "image/webp", WEBP_BYTES);

        String url = avatarStorageService.store(3L, file);

        assertThat(url).isEqualTo("/courier/3/avatar");
    }

    @Test
    void store_withEmptyFile_throwsInvalidAvatarException() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> avatarStorageService.store(1L, file))
                .isInstanceOf(InvalidAvatarException.class);
    }

    @Test
    void store_withOversizedFile_throwsInvalidAvatarException() {
        byte[] oversized = new byte[6 * 1024 * 1024];
        System.arraycopy(JPEG_BYTES, 0, oversized, 0, JPEG_BYTES.length);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> avatarStorageService.store(1L, file))
                .isInstanceOf(InvalidAvatarException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void store_withUnsupportedType_throwsInvalidAvatarException() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.gif", "image/gif",
                new byte[]{'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0});

        assertThatThrownBy(() -> avatarStorageService.store(1L, file))
                .isInstanceOf(InvalidAvatarException.class);
    }

    @Test
    void store_withSpoofedContentTypeButInvalidBytes_throwsInvalidAvatarException() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg",
                "not really an image".getBytes());

        assertThatThrownBy(() -> avatarStorageService.store(1L, file))
                .isInstanceOf(InvalidAvatarException.class);
    }

    @Test
    void store_replacingExistingAvatarWithDifferentExtension_removesOldFile() throws IOException {
        avatarStorageService.store(1L, new MockMultipartFile("file", "avatar.png", "image/png", PNG_BYTES));
        avatarStorageService.store(1L, new MockMultipartFile("file", "avatar.jpg", "image/jpeg", JPEG_BYTES));

        try (var stream = java.nio.file.Files.newDirectoryStream(tempDir, "avatar-1.*")) {
            long count = 0;
            for (Path ignored : stream) {
                count++;
            }
            assertThat(count).isEqualTo(1);
        }
    }

    @Test
    void load_whenNoAvatarStored_returnsEmpty() {
        assertThat(avatarStorageService.load(99L)).isEmpty();
    }

    @Test
    void load_afterStoringJpeg_returnsResourceWithJpegMediaType() throws IOException {
        avatarStorageService.store(4L, new MockMultipartFile("file", "avatar.jpg", "image/jpeg", JPEG_BYTES));

        Optional<StoredAvatarResource> loaded = avatarStorageService.load(4L);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
        try (InputStream in = loaded.get().resource().getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(JPEG_BYTES);
        }
    }
}