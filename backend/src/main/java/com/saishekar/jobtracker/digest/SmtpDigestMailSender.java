package com.saishekar.jobtracker.digest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends digest mail over SMTP through Spring's {@link JavaMailSender}.
 * The sender is resolved lazily: with no {@code spring.mail.host}
 * configured Spring Boot creates no mail sender bean, and a send attempt
 * then fails with a clear message instead of the application refusing to
 * start on a machine that never enables the digest.
 */
@Component
public class SmtpDigestMailSender implements DigestMailSender {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public SmtpDigestMailSender(ObjectProvider<JavaMailSender> mailSender,
                                @Value("${app.digest.from:no-reply@job-tracker.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException(
                    "No mail sender is configured: set spring.mail.host (MAIL_HOST) to send the weekly digest");
        }
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
