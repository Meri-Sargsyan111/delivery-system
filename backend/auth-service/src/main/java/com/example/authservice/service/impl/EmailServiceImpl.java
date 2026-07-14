package com.example.authservice.service.impl;

import com.example.authservice.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.from-name:Delivery System}")
    private String fromName;

    private final JavaMailSender mailSender;

    /**
     * @Async so a slow or unreachable SMTP server never adds latency to - or fails - the
     * registration HTTP response (see AsyncConfig for the executor). Every failure path is
     * caught here rather than propagated, per the no-throw contract in EmailService.
     */
    @Override
    @Async
    public void sendWelcomeEmail(String toEmail, String firstName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to Delivery System, " + firstName + "!");
            helper.setText(buildHtmlBody(firstName), true);

            mailSender.send(message);
            log.info("Welcome email sent to {}", toEmail);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            log.warn("Failed to build welcome email for {}, continuing without it: {}", toEmail, ex.getMessage());
        } catch (Exception ex) {
            log.warn("Failed to send welcome email to {}, continuing without it: {}", toEmail, ex.getMessage());
        }
    }

    private String buildHtmlBody(String firstName) {
        return """
                <!DOCTYPE html>
                <html>
                  <body style="margin:0;padding:0;background-color:#0f172a;font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#0f172a;padding:32px 0;">
                      <tr>
                        <td align="center">
                          <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="background-color:#111827;border-radius:14px;overflow:hidden;border:1px solid rgba(255,255,255,0.08);">
                            <tr>
                              <td style="background:linear-gradient(135deg,#6366f1 0%%,#8b5cf6 100%%);padding:28px 32px;">
                                <span style="color:#ffffff;font-size:20px;font-weight:700;">&#128666; Delivery System</span>
                              </td>
                            </tr>
                            <tr>
                              <td style="padding:32px;color:#f1f5f9;">
                                <h1 style="margin:0 0 16px;font-size:22px;">Welcome, %s!</h1>
                                <p style="margin:0 0 16px;font-size:15px;line-height:1.6;color:#94a3b8;">
                                  Your account has been created successfully. You're all set to start using
                                  Delivery System to create, dispatch and track deliveries in real time.
                                </p>
                                <p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:#94a3b8;">
                                  If you didn't create this account, you can safely ignore this email.
                                </p>
                                <p style="margin:0;font-size:13px;color:#64748b;">
                                  &mdash; The Delivery System Team
                                </p>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """.formatted(escapeHtml(firstName));
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
