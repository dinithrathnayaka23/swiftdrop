package com.swiftdrop.merchant.service;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.api.ApiKeyApi;
import com.swiftdrop.merchant.api.MerchantSummary;
import com.swiftdrop.merchant.api.ValidatedApiKey;
import com.swiftdrop.merchant.config.MerchantApiKeyProperties;
import com.swiftdrop.merchant.dto.response.ApiKeyResponse;
import com.swiftdrop.merchant.dto.response.GeneratedApiKeyResponse;
import com.swiftdrop.merchant.mapper.ApiKeyMapper;
import com.swiftdrop.merchant.mapper.MerchantMapper;
import com.swiftdrop.merchant.model.MerchantApiCredential;
import com.swiftdrop.merchant.repository.MerchantApiCredentialRepository;
import com.swiftdrop.merchant.repository.MerchantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant API keys: issues them and, as {@link ApiKeyApi}, checks them on every order API call.
 * The raw key is returned once, when it is generated; only its hash is stored, and only key IDs
 * are logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantApiKeyService implements ApiKeyApi {

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
        return issue(merchantId);
    }

    /** All keys of the merchant, newest first. Callers pass an approved merchant's ID. */
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(UUID merchantId) {
        Instant now = Instant.now();
        return credentialRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(credential -> ApiKeyMapper.toResponse(credential, now))
                .toList();
    }

    /** Stops a key working at once. Revoking a key that is already revoked changes nothing. */
    @Transactional
    public ApiKeyResponse revoke(UUID merchantId, UUID apiCredentialId) {
        MerchantApiCredential credential = findOwned(merchantId, apiCredentialId);
        Instant now = Instant.now();
        if (credential.getRevokedAt() == null) {
            credential.setRevokedAt(now);
            log.info("API key {} revoked for merchant {}", credential.getKeyIdentifier(), merchantId);
        }
        return ApiKeyMapper.toResponse(credential, now);
    }

    /**
     * Revokes an active key and issues its replacement in one step. Works even at the active-key
     * limit, since the count doesn't change.
     */
    @Transactional
    public GeneratedApiKeyResponse rotate(UUID merchantId, UUID apiCredentialId) {
        MerchantApiCredential old = findOwned(merchantId, apiCredentialId);
        Instant now = Instant.now();
        if (!isActive(old, now)) {
            throw new ConflictException("Only an active API key can be rotated. Create a new key instead.");
        }
        old.setRevokedAt(now);
        GeneratedApiKeyResponse replacement = issue(merchantId);
        log.info("API key {} rotated to {} for merchant {}", old.getKeyIdentifier(), replacement.keyId(), merchantId);
        return replacement;
    }

    private GeneratedApiKeyResponse issue(UUID merchantId) {
        ApiKeyCodec.NewKey key = codec.generate();
        MerchantApiCredential credential = new MerchantApiCredential();
        credential.setMerchant(merchantRepository.getReferenceById(merchantId));
        credential.setKeyIdentifier(key.keyId());
        credential.setApiKeyHash(codec.hash(key.rawKey()));
        MerchantApiCredential saved = credentialRepository.saveAndFlush(credential);
        log.info("API key {} created for merchant {}", key.keyId(), merchantId);
        return new GeneratedApiKeyResponse(saved.getId(), key.keyId(), key.rawKey(), saved.getCreatedAt());
    }

    @Override
    @Transactional
    public Optional<ValidatedApiKey> validate(String apiKey) {
        return findValid(apiKey).map(credential -> new ValidatedApiKey(
                credential.getMerchant().getId(), credential.getId(), credential.getKeyIdentifier()));
    }

    @Override
    @Transactional
    public Optional<MerchantSummary> getMerchantForKey(String apiKey) {
        return findValid(apiKey).map(credential -> MerchantMapper.toSummary(credential.getMerchant()));
    }

    @Override
    @Transactional
    public Optional<ValidatedApiKey> validateCredential(UUID merchantId, UUID apiCredentialId) {
        if (merchantId == null || apiCredentialId == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return checkedUse(credentialRepository.findByIdAndMerchantId(apiCredentialId, merchantId)
                .filter(credential -> isActive(credential, now)), now)
                .map(credential -> new ValidatedApiKey(
                        credential.getMerchant().getId(), credential.getId(), credential.getKeyIdentifier()));
    }

    /**
     * The active credential matching the raw key, or empty when the key is null, malformed, unknown,
     * wrong, revoked or expired. A valid key of a merchant that is no longer approved throws
     * {@link com.swiftdrop.merchant.exception.MerchantNotApprovedException} (403). Records the use.
     */
    private Optional<MerchantApiCredential> findValid(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return checkedUse(codec.keyIdOf(rawKey)
                .flatMap(credentialRepository::findWithMerchantByKeyIdentifier)
                .filter(credential -> codec.matches(rawKey, credential.getApiKeyHash()))
                .filter(credential -> isActive(credential, now)), now);
    }

    /** Throws 403 when the active credential's merchant is no longer approved; records the use. */
    private Optional<MerchantApiCredential> checkedUse(Optional<MerchantApiCredential> found, Instant now) {
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MerchantApiCredential credential = found.get();
        merchantAccessService.checkApproved(credential.getMerchant());
        if (credential.getLastUsedAt() == null || credential.getLastUsedAt().isBefore(now.minus(LAST_USED_PRECISION))) {
            credential.setLastUsedAt(now);
        }
        return found;
    }

    /** Not found rather than forbidden for another merchant's key, so key IDs can't be probed. */
    private MerchantApiCredential findOwned(UUID merchantId, UUID apiCredentialId) {
        return credentialRepository.findByIdAndMerchantId(apiCredentialId, merchantId)
                .orElseThrow(() -> new NotFoundException("API key not found"));
    }

    private static boolean isActive(MerchantApiCredential credential, Instant now) {
        return ApiKeyMapper.statusOf(credential, now) == ApiKeyResponse.Status.ACTIVE;
    }
}
