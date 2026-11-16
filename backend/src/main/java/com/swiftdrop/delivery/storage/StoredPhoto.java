package com.swiftdrop.delivery.storage;

/**
 * A proof photo as stored.
 *
 * @param storageKey  where it is, relative to the storage folder (e.g. {@code 2026/11/<uuid>.jpg})
 * @param contentType always {@code image/jpeg}: every photo is re-encoded
 * @param sizeBytes   size of the stored file
 * @param sha256      hex SHA-256 of the stored file
 */
public record StoredPhoto(String storageKey, String contentType, long sizeBytes, String sha256) {
}
