package com.swiftdrop.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.util.Properties;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private EmailService emailService;

    @BeforeEach
    void setUp() throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        // Runs deliveries on the calling thread, so tests can check the result straight away.
        emailService = new EmailService(mailSender, Runnable::run, "swiftdrop.app@gmail.com", "SwiftDrop",
                "smtp.gmail.com", "swiftdrop.app@gmail.com", "app-password");
    }

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsHtmlWithPlainTextAlternativeFromTheNamedSender() throws Exception {
        emailService.send("jane@example.com", "Reset your SwiftDrop password", "plain body", "<p>html body</p>");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();

        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("swiftdrop.app@gmail.com");
        assertThat(from.getPersonal()).isEqualTo("SwiftDrop");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("jane@example.com");
        assertThat(message.getSubject()).isEqualTo("Reset your SwiftDrop password");
        assertThat(message.getContent()).isInstanceOf(MimeMultipart.class);

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        assertThat(raw.toString()).contains("plain body").contains("<p>html body</p>");
    }

    @Test
    void sendsPlainTextOnly() throws Exception {
        emailService.send("jane@example.com", "Subject", "only text");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getContent()).isEqualTo("only text");
    }

    @Test
    void insideATransactionTheEmailWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        emailService.send("jane@example.com", "Subject", "text");
        verify(mailSender, never()).send(any(MimeMessage.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void aRolledBackTransactionSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        emailService.send("jane@example.com", "Subject", "text");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void aFullOrStoppedExecutorNeverFailsTheCaller() throws Exception {
        var rejecting = new EmailService(mailSender, task -> {
            throw new RejectedExecutionException("shutting down");
        }, "swiftdrop.app@gmail.com", "SwiftDrop", "smtp.gmail.com", "", "");

        assertThatCode(() -> rejecting.send("jane@example.com", "Subject", "text")).doesNotThrowAnyException();
    }

    @Test
    void aMailServerFailureNeverPropagatesToTheCaller() {
        doThrow(new MailSendException("535 Username and Password not accepted"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> emailService.send("jane@example.com", "Subject", "text", "<p>html</p>"))
                .doesNotThrowAnyException();
    }
}
