package com.swiftdrop.merchant.api;

import java.util.UUID;

/**
 * Public API of the merchant module. Other modules (merchant portal, merchant order API,
 * notifications) look up merchants through this interface and never use the merchant repository or
 * entity directly.
 */
public interface MerchantApi {

    /** Whether the merchant exists and is APPROVED. False for unknown IDs. */
    boolean isApproved(UUID merchantId);

    /**
     * The merchant with this ID.
     *
     * @throws com.swiftdrop.common.exception.NotFoundException if there is none
     */
    MerchantSummary getMerchant(UUID merchantId);
}
