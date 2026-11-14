package com.swiftdrop.delivery.storage;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.NotFoundException;

/**
 * Where proof-of-delivery photos are kept. Photos are personal data (they can show the recipient and
 * their home): they are only read back through the checked photo endpoint, never served directly.
 */
public interface ProofStorage {

    /**
     * Checks the upload is a real JPEG or PNG, re-encodes it as a JPEG of at most
     * {@link LocalFileProofStorage#MAX_EDGE_PX} px without any metadata (no GPS), and stores it. If
     * the surrounding transaction rolls back, the stored file is deleted again.
     *
     * @throws BadRequestException if the upload is empty, too large, or not a readable JPEG/PNG
     */
    StoredPhoto store(byte[] upload);

    /** @throws NotFoundException if there is no file for this key */
    byte[] read(String storageKey);

    /** Removes the file; does nothing if it is already gone. */
    void delete(String storageKey);
}
