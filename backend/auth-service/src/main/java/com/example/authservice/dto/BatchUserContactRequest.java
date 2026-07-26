package com.example.authservice.dto;

import java.util.List;
import java.util.UUID;

public record BatchUserContactRequest(List<UUID> ids) {
}