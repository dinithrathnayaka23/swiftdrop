package com.swiftdrop.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends email through the configured SMTP server (Gmail in production-like setups, Mailpit locally).
 *
 * <ul>
 *   <li>Called inside a database transaction, the email waits until the transaction has committed,
 *       and is dropped if it rolls back: nobody is told about a change that didn't happen.</li>
 *   <li>Delivery runs in the background (Spring Boot's task executor): a slow or failing mail
 *       server never delays or fails an API request.</li>
 *   <li>Failures are logged without the recipient or the body.</li>
 * </ul>
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final Executor executor;
    private final InternetAddress sender;
    private final String smtpHost;
    private final String smtpUsername;
    private final String smtpPassword;

    public EmailService(JavaMailSender mailSender,
                        @Qualifier("applicationTaskExecutor") Executor executor,
                        @Value("${swiftdrop.mail.from}") String fromAddress,
                        @Value("${swiftdrop.mail.from-name:SwiftDrop}") String fromName,
                        @Value("${spring.mail.host:}") String smtpHost,
                        @Value("${spring.mail.username:}") String smtpUsername,
                        @Value("${spring.mail.password:}") String smtpPassword) throws UnsupportedEncodingException {
        this.mailSender = mailSender;
        this.executor = executor;
        this.sender = new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name());
        this.smtpHost = smtpHost;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
    }

    /** Warn early when SMTP credentials are half-configured (e.g. Gmail address set, app password not yet). */
    @EventListener(ApplicationReadyEvent.class)
    public void checkConfiguration() {
        if (!smtpUsername.isBlank() && smtpPassword.isBlank()) {
            log.warn("MAIL_USERNAME is set but MAIL_PASSWORD is empty: emails via {} will fail until the "
                    + "app password is added to .env", smtpHost);
        }
    }

    /** Plain-text email. */
    public void send(String to, String subject, String text) {
        sendAfterCommit(() -> deliver(to, subject, text, null), subject);
    }

    /** HTML email with a plain-text alternative for clients that don't show HTML. */
    public void send(String to, String subject, String text, String html) {
        sendAfterCommit(() -> deliver(to, subject, text, html), subject);
    }

    private void sendAfterCommit(Runnable delivery, String subject) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(delivery, subject);
                }
            });
        } else {
            dispatch(delivery, subject);
        }
    }

    private void dispatch(Runnable delivery, String subject) {
        try {
            executor.execute(delivery);
        } catch (RejectedExecutionException ex) {
            // Executor shutting down or saturated: never let that fail the request that sent the email.
            log.warn("Email '{}' could not be queued: {}", subject, ex.getClass().getSimpleName());
        }
    }

    private void deliver(String to, String subject, String text, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, html != null, StandardCharsets.UTF_8.name());
            helper.setFrom(sender);
            helper.setTo(to);
            helper.setSubject(subject);
            if (html != null) {
                helper.setText(text, html);
            } else {
                helper.setText(text);
            }
            mailSender.send(message);
            log.debug("Email '{}' sent", subject);
        } catch (MessagingException | RuntimeException ex) { // RuntimeException includes MailException
            // No recipient or body in the log: both can contain personal data or secrets (reset links).
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
            log.warn("Email '{}' could not be sent via {}: {} ({})", subject, smtpHost,
                    ex.getClass().getSimpleName(), cause.getClass().getSimpleName());
        }
    }
}
