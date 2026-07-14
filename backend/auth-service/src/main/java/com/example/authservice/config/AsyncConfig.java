package com.example.authservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables {@code @Async} - see EmailServiceImpl.sendWelcomeEmail, which relies on it. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
