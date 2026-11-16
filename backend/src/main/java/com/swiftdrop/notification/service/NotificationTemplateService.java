package com.swiftdrop.notification.service;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders email (plain-text + HTML) and SMS notification templates for parcel status milestones.
 */
@Service
public class NotificationTemplateService {

    public record RenderedNotification(
            String subject,
            String plainText,
            String htmlBody,
            String smsText) {
    }

    public RenderedNotification render(
            String status,
            String trackingNumber,
            String companyName,
            String supportEmail,
            String supportPhone) {
        String safeTracking = (trackingNumber != null && !trackingNumber.isBlank()) ? trackingNumber : "N/A";
        String brand = (companyName != null && !companyName.isBlank()) ? companyName : "SwiftDrop";
        String email = (supportEmail != null && !supportEmail.isBlank()) ? supportEmail : "support@swiftdrop.lk";
        String phone = (supportPhone != null && !supportPhone.isBlank()) ? supportPhone : "+94 11 234 5678";

        String subject = buildSubject(status, safeTracking);
        String headline = buildHeadline(status);
        String summary = buildMessage(status, safeTracking);
        String badgeColor = resolveBadgeColor(status);
        String plainText = buildPlainText(brand, headline, summary, safeTracking, email, phone);
        String htmlBody = buildHtml(brand, headline, summary, safeTracking, status, badgeColor, email, phone);
        String smsText = buildSms(brand, status, safeTracking);

        return new RenderedNotification(subject, plainText, htmlBody, smsText);
    }

    public String buildSubject(String status, String trackingNumber) {
        String normalized = normalizeStatus(status);
        return switch (normalized) {
            case "CREATED", "BOOKED" -> "Booking Confirmed (" + trackingNumber + ")";
            case "PICKED_UP" -> "Parcel Picked Up (" + trackingNumber + ")";
            case "IN_TRANSIT" -> "Parcel in Transit (" + trackingNumber + ")";
            case "OUT_FOR_DELIVERY" -> "Parcel Out for Delivery (" + trackingNumber + ")";
            case "DELIVERED" -> "Parcel Delivered Successfully (" + trackingNumber + ")";
            case "FAILED", "DELIVERY_ATTEMPTED" -> "Delivery Attempt Failed (" + trackingNumber + ")";
            case "RETURNED" -> "Parcel Returned to Sender (" + trackingNumber + ")";
            case "CANCELLED" -> "Parcel Delivery Cancelled (" + trackingNumber + ")";
            default -> "Parcel Status Update (" + trackingNumber + ")";
        };
    }

    public String buildMessage(String status, String trackingNumber) {
        String normalized = normalizeStatus(status);
        return switch (normalized) {
            case "CREATED", "BOOKED" ->
                "Your parcel " + trackingNumber + " has been booked and is awaiting courier pickup.";
            case "PICKED_UP" ->
                "Your parcel " + trackingNumber + " has been picked up from the sender and registered into our network.";
            case "IN_TRANSIT" ->
                "Your parcel " + trackingNumber + " has departed the sorting hub and is in transit to the destination zone.";
            case "OUT_FOR_DELIVERY" ->
                "Your parcel " + trackingNumber + " is out for delivery with our courier. Please be available to receive it.";
            case "DELIVERED" ->
                "Your parcel " + trackingNumber + " has been delivered successfully. Proof of delivery is available in your dashboard.";
            case "FAILED", "DELIVERY_ATTEMPTED" ->
                "A delivery attempt for parcel " + trackingNumber + " was unsuccessful. Our dispatch team will arrange a follow-up.";
            case "RETURNED" ->
                "Your parcel " + trackingNumber + " could not be completed and is being returned to the sender.";
            case "CANCELLED" ->
                "Your parcel " + trackingNumber + " booking has been cancelled.";
            default ->
                "Your parcel " + trackingNumber + " status is now " + (status != null ? status : "UPDATED") + ".";
        };
    }

    public String buildSms(String companyName, String status, String trackingNumber) {
        String brand = (companyName != null && !companyName.isBlank()) ? companyName : "SwiftDrop";
        String normalized = normalizeStatus(status);
        return switch (normalized) {
            case "PICKED_UP" ->
                brand + ": Parcel " + trackingNumber + " has been picked up and entered our delivery network.";
            case "IN_TRANSIT" ->
                brand + ": Parcel " + trackingNumber + " is in transit to your local delivery hub.";
            case "OUT_FOR_DELIVERY" ->
                brand + ": Parcel " + trackingNumber + " is OUT FOR DELIVERY today. Please keep your phone reachable.";
            case "DELIVERED" ->
                brand + ": Parcel " + trackingNumber + " has been DELIVERED. Thank you for choosing " + brand + "!";
            case "FAILED", "DELIVERY_ATTEMPTED" ->
                brand + " Alert: Delivery attempt for parcel " + trackingNumber + " was unsuccessful. We will contact you shortly.";
            default ->
                brand + ": Parcel " + trackingNumber + " status updated to " + normalized + ".";
        };
    }

    private String buildHeadline(String status) {
        String normalized = normalizeStatus(status);
        return switch (normalized) {
            case "CREATED", "BOOKED" -> "Parcel Booking Confirmed";
            case "PICKED_UP" -> "Parcel Picked Up";
            case "IN_TRANSIT" -> "Parcel In Transit";
            case "OUT_FOR_DELIVERY" -> "Out for Delivery Today";
            case "DELIVERED" -> "Delivered Successfully";
            case "FAILED", "DELIVERY_ATTEMPTED" -> "Delivery Exception Notice";
            case "RETURNED" -> "Returned to Sender";
            case "CANCELLED" -> "Delivery Cancelled";
            default -> "Delivery Status Update";
        };
    }

    private String resolveBadgeColor(String status) {
        String normalized = normalizeStatus(status);
        return switch (normalized) {
            case "DELIVERED" -> "#059669";
            case "FAILED", "DELIVERY_ATTEMPTED", "CANCELLED" -> "#e11d48";
            case "OUT_FOR_DELIVERY", "IN_TRANSIT" -> "#2563eb";
            case "PICKED_UP" -> "#d97706";
            default -> "#475569";
        };
    }

    private String buildPlainText(
            String brand,
            String headline,
            String summary,
            String trackingNumber,
            String supportEmail,
            String supportPhone) {
        return headline + "\n"
                + "========================================\n\n"
                + summary + "\n\n"
                + "Tracking Number: " + trackingNumber + "\n\n"
                + "Need assistance? Contact " + brand + " Support at " + supportEmail
                + " or call " + supportPhone + ".\n";
    }

    private String buildHtml(
            String brand,
            String headline,
            String summary,
            String trackingNumber,
            String rawStatus,
            String badgeColor,
            String supportEmail,
            String supportPhone) {
        String escBrand = HtmlUtils.htmlEscape(brand);
        String escHeadline = HtmlUtils.htmlEscape(headline);
        String escSummary = HtmlUtils.htmlEscape(summary);
        String escTracking = HtmlUtils.htmlEscape(trackingNumber);
        String escStatus = HtmlUtils.htmlEscape(normalizeStatus(rawStatus).replace('_', ' '));
        String escEmail = HtmlUtils.htmlEscape(supportEmail);
        String escPhone = HtmlUtils.htmlEscape(supportPhone);

        return "<!DOCTYPE html>"
                + "<html lang=\"en\"><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;padding:24px;background-color:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;color:#0f172a;\">"
                + "<div style=\"max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;overflow:hidden;\">"
                + "<div style=\"background:#0f172a;padding:20px 24px;color:#ffffff;\">"
                + "<span style=\"font-size:18px;font-weight:700;letter-spacing:-0.02em;\">" + escBrand + "</span>"
                + "</div>"
                + "<div style=\"padding:24px;\">"
                + "<span style=\"display:inline-block;padding:4px 10px;border-radius:9999px;font-size:11px;font-weight:700;text-transform:uppercase;color:#ffffff;background-color:" + badgeColor + ";\">"
                + escStatus + "</span>"
                + "<h1 style=\"margin:16px 0 8px;font-size:20px;font-weight:700;color:#0f172a;\">" + escHeadline + "</h1>"
                + "<p style=\"margin:0 0 20px;font-size:14px;line-height:1.6;color:#475569;\">" + escSummary + "</p>"
                + "<div style=\"background:#f1f5f9;border-radius:12px;padding:14px 16px;margin-bottom:20px;\">"
                + "<div style=\"font-size:11px;font-weight:600;text-transform:uppercase;color:#64748b;\">Tracking Number</div>"
                + "<div style=\"font-size:16px;font-weight:700;font-family:monospace;color:#0f172a;margin-top:4px;\">" + escTracking + "</div>"
                + "</div>"
                + "<hr style=\"border:none;border-top:1px solid #e2e8f0;margin:20px 0;\">"
                + "<p style=\"margin:0;font-size:12px;color:#64748b;\">Questions? Contact " + escBrand + " Support at "
                + "<a href=\"mailto:" + escEmail + "\" style=\"color:#2563eb;text-decoration:none;\">" + escEmail + "</a>"
                + " or " + escPhone + ".</p>"
                + "</div></div></body></html>";
    }

    private static String normalizeStatus(String status) {
        return status != null ? status.trim().toUpperCase() : "";
    }
}
