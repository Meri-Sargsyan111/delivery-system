package com.example.authservice.service;

import com.example.authservice.service.impl.EmailServiceImpl;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock private JavaMailSender mailSender;

    private EmailServiceImpl emailService;

    /**
     * @Value fields aren't set by Mockito's @InjectMocks (Spring-only injection) -
     * ReflectionTestUtils bridges the gap without touching production code, matching the
     * pattern already used for notification-service's mail-related tests.
     */
    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(mailSender);
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@delivery-system.example");
        ReflectionTestUtils.setField(emailService, "fromName", "Delivery System");

        // @Async methods still run synchronously on the calling thread when invoked
        // directly on the bean (no Spring proxy) in a plain unit test - no executor needed.
        // lenient(): some tests below re-stub createMimeMessage() to throw instead.
        lenient().when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendWelcomeEmail_sendsToTheGivenAddressWithBrandedSubject() throws Exception {
        emailService.sendWelcomeEmail("newuser@example.com", "Alice");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendWelcomeEmail_bodyContainsFirstNameAndWelcomeMessage() throws Exception {
        emailService.sendWelcomeEmail("newuser@example.com", "Alice");

        org.mockito.ArgumentCaptor<MimeMessage> captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getSubject()).contains("Alice");
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("newuser@example.com");

        String body = (String) sent.getContent();
        assertThat(body).contains("Welcome, Alice!");
        assertThat(body).contains("Delivery System");
        assertThat(body).contains("account has been created successfully");
    }

    @Test
    void sendWelcomeEmail_escapesHtmlInFirstName() throws Exception {
        emailService.sendWelcomeEmail("newuser@example.com", "<script>alert(1)</script>");

        org.mockito.ArgumentCaptor<MimeMessage> captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        String body = (String) captor.getValue().getContent();
        assertThat(body).doesNotContain("<script>");
        assertThat(body).contains("&lt;script&gt;");
    }

    @Test
    void sendWelcomeEmail_whenMailSenderThrows_swallowsExceptionInsteadOfPropagating() {
        doThrow(new org.springframework.mail.MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> emailService.sendWelcomeEmail("newuser@example.com", "Alice"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendWelcomeEmail_whenCreateMimeMessageThrows_swallowsExceptionInsteadOfPropagating() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> emailService.sendWelcomeEmail("newuser@example.com", "Alice"))
                .doesNotThrowAnyException();
    }
}