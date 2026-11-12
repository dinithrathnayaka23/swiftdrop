package com.swiftdrop.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.merchant.config.MerchantApiKeyProperties;
import com.swiftdrop.merchant.dto.AuthenticatedMerchant;
import com.swiftdrop.merchant.dto.response.GeneratedApiKeyResponse;
import com.swiftdrop.merchant.exception.MerchantNotApprovedException;
import com.swiftdrop.merchant.model.Merchant;
import com.swiftdrop.merchant.model.MerchantApiCredential;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.repository.MerchantApiCredentialRepository;
import com.swiftdrop.merchant.repository.MerchantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantApiKeyServiceTest {

    @Mock private MerchantApiCredentialRepository credentialRepository;
    @Mock private MerchantRepository merchantRepository;

    private final ApiKeyCodec codec = new ApiKeyCodec(new MerchantApiKeyProperties(ApiKeyCodecTest.PEPPER, 0));
    private MerchantApiKeyService apiKeyService;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        apiKeyService = new MerchantApiKeyService(credentialRepository, merchantRepository,
                new MerchantAccessService(merchantRepository), codec,
                new MerchantApiKeyProperties(ApiKeyCodecTest.PEPPER, 2));
        merchant = new Merchant();
        merchant.setId(UUID.randomUUID());
        merchant.setApprovalStatus(MerchantApprovalStatus.APPROVED);
    }

    /** Stores a credential for a freshly generated key and returns the raw key. */
    private String storedKey(MerchantApiCredential credential) {
        ApiKeyCodec.NewKey key = codec.generate();
        credential.setId(UUID.randomUUID());
        credential.setMerchant(merchant);
        credential.setKeyIdentifier(key.keyId());
        credential.setApiKeyHash(codec.hash(key.rawKey()));
        when(credentialRepository.findWithMerchantByKeyIdentifier(key.keyId())).thenReturn(Optional.of(credential));
        return key.rawKey();
    }

    @Test
    void generatedKeyIsReturnedOnceAndOnlyItsHashIsStored() {
        when(credentialRepository.countActive(eq(merchant.getId()), any())).thenReturn(1L);
        when(merchantRepository.getReferenceById(merchant.getId())).thenReturn(merchant);
        when(credentialRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        GeneratedApiKeyResponse response = apiKeyService.generate(merchant.getId());

        ArgumentCaptor<MerchantApiCredential> saved = ArgumentCaptor.forClass(MerchantApiCredential.class);
        verify(credentialRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getMerchant()).isSameAs(merchant);
        assertThat(saved.getValue().getKeyIdentifier()).isEqualTo(response.keyId());
        assertThat(saved.getValue().getApiKeyHash()).isNotEqualTo(response.apiKey());
        assertThat(codec.matches(response.apiKey(), saved.getValue().getApiKeyHash())).isTrue();
    }

    @Test
    void merchantCannotHoldMoreThanTheMaximumActiveKeys() {
        when(credentialRepository.countActive(eq(merchant.getId()), any())).thenReturn(2L);

        assertThatThrownBy(() -> apiKeyService.generate(merchant.getId()))
                .isInstanceOf(ConflictException.class);
        verify(credentialRepository, never()).saveAndFlush(any());
    }

    @Test
    void validKeyIdentifiesItsMerchantAndRecordsTheUse() {
        MerchantApiCredential credential = new MerchantApiCredential();
        String rawKey = storedKey(credential);

        Optional<AuthenticatedMerchant> result = apiKeyService.authenticate(rawKey);

        assertThat(result).contains(new AuthenticatedMerchant(
                merchant.getId(), credential.getId(), credential.getKeyIdentifier()));
        assertThat(credential.getLastUsedAt()).isNotNull();
    }

    @Test
    void recentUseIsNotWrittenAgain() {
        MerchantApiCredential credential = new MerchantApiCredential();
        Instant lastUsed = Instant.now().minusSeconds(10);
        credential.setLastUsedAt(lastUsed);

        apiKeyService.authenticate(storedKey(credential));

        assertThat(credential.getLastUsedAt()).isEqualTo(lastUsed);
    }

    @Test
    void wrongSecretForAKnownKeyIdIsRejected() {
        String rawKey = storedKey(new MerchantApiCredential());
        String tampered = rawKey.substring(0, rawKey.length() - 1) + (rawKey.endsWith("A") ? "B" : "A");

        assertThat(apiKeyService.authenticate(tampered)).isEmpty();
    }

    @Test
    void unknownKeyIsRejected() {
        when(credentialRepository.findWithMerchantByKeyIdentifier(any())).thenReturn(Optional.empty());

        assertThat(apiKeyService.authenticate(codec.generate().rawKey())).isEmpty();
    }

    @Test
    void malformedKeyIsRejectedWithoutADatabaseLookUp() {
        assertThat(apiKeyService.authenticate("Bearer something")).isEmpty();
        verifyNoInteractions(credentialRepository);
    }

    @Test
    void revokedKeyIsRejected() {
        MerchantApiCredential credential = new MerchantApiCredential();
        credential.setRevokedAt(Instant.now().minus(Duration.ofHours(1)));

        assertThat(apiKeyService.authenticate(storedKey(credential))).isEmpty();
    }

    @Test
    void expiredKeyIsRejected() {
        MerchantApiCredential credential = new MerchantApiCredential();
        credential.setExpiresAt(Instant.now().minusSeconds(1));

        assertThat(apiKeyService.authenticate(storedKey(credential))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = MerchantApprovalStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void validKeyOfAMerchantThatIsNotApprovedIsForbidden(MerchantApprovalStatus status) {
        merchant.setApprovalStatus(status);
        MerchantApiCredential credential = new MerchantApiCredential();
        String rawKey = storedKey(credential);

        assertThatThrownBy(() -> apiKeyService.authenticate(rawKey))
                .isInstanceOf(MerchantNotApprovedException.class);
        assertThat(credential.getLastUsedAt()).isNull();
    }
}
