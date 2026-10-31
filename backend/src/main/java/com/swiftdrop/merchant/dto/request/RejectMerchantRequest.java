package com.swiftdrop.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectMerchantRequest(
        @NotBlank(message = "Please give a reason for the rejection")
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason) {
}
