package com.swiftdrop.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SmsServiceTest {

    private final SmsService smsService = new SmsService();

    @Test
    void sendAcceptsValidInternationalPhoneNumber() {
        assertThatCode(() -> smsService.send("+94 77 123 4567", "SwiftDrop: Parcel SD123 delivered", "SD123"))
                .doesNotThrowAnyException();
    }

    @Test
    void sendRejectsMalformedPhoneNumber() {
        assertThatThrownBy(() -> smsService.send("not-a-phone", "SwiftDrop alert", "SD123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void templateServiceRendersHtmlPlainTextAndSms() {
        NotificationTemplateService templateService = new NotificationTemplateService();
        NotificationTemplateService.RenderedNotification rendered = templateService.render(
                "OUT_FOR_DELIVERY",
                "SD2508220098",
                "SwiftDrop",
                "support@swiftdrop.lk",
                "+94 11 234 5678"
        );

        assertThat(rendered.subject()).isEqualTo("Parcel Out for Delivery (SD2508220098)");
        assertThat(rendered.plainText()).contains("SD2508220098");
        assertThat(rendered.htmlBody()).contains("<!DOCTYPE html>").contains("SD2508220098");
        assertThat(rendered.smsText()).contains("OUT FOR DELIVERY").contains("SD2508220098");
    }
}
