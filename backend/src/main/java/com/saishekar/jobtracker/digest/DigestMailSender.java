package com.saishekar.jobtracker.digest;

/**
 * The one operation the digest feature needs from a mail system. Keeping it
 * behind an interface means the digest can be rendered and tested without an
 * SMTP server, and the transport can change without touching the digest
 * itself.
 */
public interface DigestMailSender {

    void send(String to, String subject, String body);
}
