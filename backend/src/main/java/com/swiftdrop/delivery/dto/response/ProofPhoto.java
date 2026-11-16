package com.swiftdrop.delivery.dto.response;

/** A proof photo's bytes, for the photo endpoint. */
public record ProofPhoto(byte[] content, String contentType) {
}
