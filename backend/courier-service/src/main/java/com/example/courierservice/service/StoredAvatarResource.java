package com.example.courierservice.service;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record StoredAvatarResource(Resource resource, MediaType mediaType) {
}