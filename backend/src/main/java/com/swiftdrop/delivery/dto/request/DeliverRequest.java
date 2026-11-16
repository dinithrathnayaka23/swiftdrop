package com.swiftdrop.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Proof of delivery, sent as {@code multipart/form-data}: who received the parcel, an optional note
 * and an optional photo (JPEG or PNG).
 */
public record DeliverRequest(
        @NotBlank(message = "Recipient's name is required")
        @Size(max = DeliverRequest.MAX_RECIPIENT_NAME, message = "Recipient's name must be at most "
                + DeliverRequest.MAX_RECIPIENT_NAME + " characters")
        String recipientName,
        @Size(max = DeliverRequest.MAX_NOTE, message = "Note must be at most " + DeliverRequest.MAX_NOTE
                + " characters")
        String note,
        MultipartFile photo) {

    public static final int MAX_RECIPIENT_NAME = 120;
    public static final int MAX_NOTE = 500;

    public boolean hasPhoto() {
        return photo != null && !photo.isEmpty();
    }
}
