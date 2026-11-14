package com.swiftdrop.merchant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.UnauthorizedException;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.merchant.dto.AuthenticatedMerchant;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthTokenControllerTest {

    @Mock
    private MerchantApiKeyService merchantApiKeyService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private OAuthTokenController controller;

    @Test
    void issuesFifteenMinuteBearerTokenForValidClientCredentials() {
        AuthenticatedMerchant merchant = new AuthenticatedMerchant(
                UUID.randomUUID(), UUID.randomUUID(), "key-123");
        when(merchantApiKeyService.authenticate("secret")).thenReturn(Optional.of(merchant));
        when(jwtService.generateMerchantApiToken(
                merchant.merchantId(), merchant.apiCredentialId(), OAuthTokenController.TOKEN_LIFETIME))
                .thenReturn("signed-token");

        var response = controller.token("client_credentials", "key-123", "secret");

        assertThat(response.access_token()).isEqualTo("signed-token");
        assertThat(response.token_type()).isEqualTo("Bearer");
        assertThat(response.expires_in()).isEqualTo(900);
    }

    @Test
    void rejectsWrongClientId() {
        AuthenticatedMerchant merchant = new AuthenticatedMerchant(
                UUID.randomUUID(), UUID.randomUUID(), "key-123");
        when(merchantApiKeyService.authenticate("secret")).thenReturn(Optional.of(merchant));

        assertThatThrownBy(() -> controller.token("client_credentials", "wrong-key", "secret"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
