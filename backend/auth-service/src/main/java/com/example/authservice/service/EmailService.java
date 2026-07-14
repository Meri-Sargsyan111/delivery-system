package com.example.authservice.service;

public interface EmailService {

    /**
     * Sends the branded welcome email for a newly registered user. Never throws - any
     * failure (unreachable SMTP host, bad credentials, etc.) is caught and logged inside
     * the implementation, so a broken mail server can never fail registration itself.
     */
    void sendWelcomeEmail(String toEmail, String firstName);
}
