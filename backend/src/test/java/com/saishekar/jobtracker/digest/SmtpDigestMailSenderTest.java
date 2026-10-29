package com.saishekar.jobtracker.digest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpDigestMailSenderTest {

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> providerReturning(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    @Test
    void sendsAPlainTextMessageFromTheConfiguredAddress() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        var sender = new SmtpDigestMailSender(providerReturning(mailSender), "digest@example.com");

        sender.send("ada@example.com", "Subject", "Body");

        var captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        var message = captor.getValue();
        assertEquals("digest@example.com", message.getFrom());
        assertArrayEquals(new String[]{"ada@example.com"}, message.getTo());
        assertEquals("Subject", message.getSubject());
        assertEquals("Body", message.getText());
    }

    @Test
    void failsClearlyWhenNoMailServerIsConfigured() {
        var sender = new SmtpDigestMailSender(providerReturning(null), "digest@example.com");

        var ex = assertThrows(IllegalStateException.class,
                () -> sender.send("ada@example.com", "Subject", "Body"));
        assertTrue(ex.getMessage().contains("MAIL_HOST"), ex.getMessage());
    }
}
