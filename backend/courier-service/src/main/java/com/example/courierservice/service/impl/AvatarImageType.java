package com.example.courierservice.service.impl;

import org.springframework.http.MediaType;

import java.util.Optional;

/**
 * Detected by magic-byte sniffing rather than trusting the client-supplied Content-Type
 * or filename extension, which are both easily spoofed. ImageIO is not used here because
 * the JDK has no built-in WEBP reader.
 */
enum AvatarImageType {

    JPEG(MediaType.IMAGE_JPEG, "jpg"),
    PNG(MediaType.IMAGE_PNG, "png"),
    WEBP(MediaType.valueOf("image/webp"), "webp");

    private final MediaType mediaType;
    private final String extension;

    AvatarImageType(MediaType mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    MediaType mediaType() {
        return mediaType;
    }

    String extension() {
        return extension;
    }

    static Optional<AvatarImageType> detect(byte[] header) {
        if (matches(header, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (matches(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    static AvatarImageType byExtension(String extension) {
        for (AvatarImageType type : values()) {
            if (type.extension.equalsIgnoreCase(extension)) {
                return type;
            }
        }
        throw new IllegalStateException("Unknown stored avatar extension: " + extension);
    }

    private static boolean matches(byte[] header, int offset, int... expected) {
        if (header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}