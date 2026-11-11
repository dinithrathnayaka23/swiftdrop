package com.swiftdrop.merchant.service;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.merchant.config.MerchantApiKeyProperties;
import com.swiftdrop.merchant.dto.AuthenticatedMerchant;
import com.swiftdrop.merchant.dto.response.GeneratedApiKeyResponse;
import com.swiftdrop.merchant.model.MerchantApiCredential;
import com.swiftdrop.merchant.repository.MerchantApiCredentialRepository;
import com.swiftdrop.merchant.repository.MerchantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant API keys: issues them and checks them on every order API call. The raw key is returned
 * once, when it is generated; only its hash is stored, and only key IDs are logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantApiKeyService {

    /** {@code last_used_at} is written at most this often, so a busy key doesn't cost a write per request. */
    static final Duration LAST_USED_PRECISION = Duration.ofMinutes(1);

    private final MerchantApiCredentialRepository credentialRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantAccessService merchantAccessService;
    private final ApiKeyCodec codec;
    private final MerchantApiKeyProperties properties;

    /** Creates a key. Callers pass the ID returned by {@link MerchantAccessService#requireApprovedMerchant}. */
    @Transactional
    public GeneratedApiKeyResponse generate(UUID merchantId) {
        if (credentialRepository.countActive(merchantId, Instant.now()) >= properties.maxActiveKeys()) {
            throw new ConflictException("You already have %d active API keys. Revoke one before creating another."
                    .formatted(properties.maxActiveKeys()));
        }
        ApiKeyCodec.NewKey key = codec.generate();
        MerchantApiCredential credential = new MerchantApiCredential();
        credential.setMerchant(merchantRepository.getReferenceById(merchantId));
        credential.setKeyIdentifier(key.keyId());
        credential.setApiKeyHash(codec.hash(key.rawKey()));
        MerchantApiCredential saved = credentialRepository.saveAndFlush(credential);
        log.info("API key {} created for merchant {}", key.keyId(), merchantId);
        return new GeneratedApiKeyResponse(saved.getId(), key.keyId(), key.rawKey(), saved.getCreatedAt());
    }

    /**
     * Checks a key from the {@code X-API-Key} header. Empty when the key is malformed, unknown,
     * wrong, revoked or expired: the caller answers 401 without saying which. A valid key of a
     * merchant that is no longer approved throws {@link com.swiftdrop.merchant.exception.MerchantNotApprovedException} (403).
     */
    @Transactional
    public Optional<AuthenticatedMerchant> authenticate(String rawKey) {
        Instant now = Instant.now();
        Optional<MerchantApiCredential> found = codec.keyIdOf(rawKey)
                .flatMap(credentialRepository::findWithMerchantByKeyIdentifier)
                .filter(credential -> codec.matches(rawKey, credential.getApiKeyHash()))
                .filter(credential -> isActive(credential, now));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MerchantApiCredential credential = found.get();
        merchantAccessService.checkApproved(credential.getMerchant());
        if (credential.getLastUsedAt() == null || credential.getLastUsedAt().isBefore(now.minus(LAST_USED_PRECISION))) {
            credential.setLastUsedAt(now);
        }
        return Optional.of(new AuthenticatedMerchant(credential.getMerchant().getId(), credential.getId()));
    }

    private static boolean isActive(MerchantApiCredential credential, Instant now) {
        return credential.getRevokedAt() == null
                && (credential.getExpiresAt() == null || credential.getExpiresAt().isAfter(now));
    }
}
