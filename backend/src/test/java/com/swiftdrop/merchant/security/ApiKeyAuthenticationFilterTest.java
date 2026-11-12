package com.swiftdrop.merchant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.SecurityErrorHandler;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.merchant.dto.AuthenticatedMerchant;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private MerchantApiKeyService merchantApiKeyService;

    @Mock
    private JwtService jwtService;

    @Mock
    private SecurityErrorHandler securityErrorHandler;

    @Mock
    private FilterChain chain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private void filter() throws Exception {
        new ApiKeyAuthenticationFilter(merchantApiKeyService, jwtService, securityErrorHandler)
                .doFilter(request, response, chain);
    }

    @Test
    void missingKeyIsRejectedBeforeTheChain() throws Exception {
        request.setRequestURI(ApiPaths.ORDERS);

        filter();

        verify(securityErrorHandler).commence(
                org.mockito.ArgumentMatchers.same(request),
                org.mockito.ArgumentMatchers.same(response),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(merchantApiKeyService, chain);
    }

    @Test
    void invalidKeyIsRejectedBeforeTheChain() throws Exception {
        request.setRequestURI(ApiPaths.ORDERS + "/SD7KQ2MX9TRB4H");
        request.addHeader("X-API-Key", "invalid");
        when(merchantApiKeyService.authenticate("invalid")).thenReturn(Optional.empty());

        filter();

        verify(securityErrorHandler).commence(
                org.mockito.ArgumentMatchers.same(request),
                org.mockito.ArgumentMatchers.same(response),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(chain);
    }

    @Test
    void validKeySetsMerchantPrincipalAndAuthority() throws Exception {
        request.setRequestURI(ApiPaths.ORDERS + "/SD7KQ2MX9TRB4H/status");
        request.addHeader("X-API-Key", "valid");
        AuthenticatedMerchant merchant = new AuthenticatedMerchant(UUID.randomUUID(), UUID.randomUUID());
        when(merchantApiKeyService.authenticate("valid")).thenReturn(Optional.of(merchant));

        filter();

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(merchant);
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MERCHANT_API");
        verify(chain).doFilter(request, response);
    }

    @Test
    void validBearerMerchantTokenSetsMerchantPrincipalAndAuthority() throws Exception {
        request.setRequestURI(ApiPaths.ORDERS + "/SD7KQ2MX9TRB4H/status");
        request.addHeader("Authorization", "Bearer merchant-token");
        UUID merchantId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        when(jwtService.parseMerchantApiToken("merchant-token"))
                .thenReturn(Optional.of(new JwtService.MerchantApiTokenClaims(merchantId, credentialId)));

        filter();

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal())
                .isEqualTo(new AuthenticatedMerchant(merchantId, credentialId));
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MERCHANT_API");
        verify(chain).doFilter(request, response);
        verifyNoInteractions(merchantApiKeyService);
    }

    @Test
    void unrelatedRoutesPassThroughWithoutApiKeyAuthentication() throws Exception {
        request.setRequestURI(ApiPaths.API_V1 + "/health");

        filter();

        verify(chain).doFilter(request, response);
        verifyNoInteractions(merchantApiKeyService, securityErrorHandler);
    }
}
