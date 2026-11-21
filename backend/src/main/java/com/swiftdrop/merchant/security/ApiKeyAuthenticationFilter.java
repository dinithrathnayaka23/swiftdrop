package com.swiftdrop.merchant.security;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.security.SecurityErrorHandler;
import com.swiftdrop.common.security.jwt.JwtService;
import com.swiftdrop.merchant.api.ApiKeyApi;
import com.swiftdrop.merchant.dto.AuthenticatedMerchant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates merchant order API requests with the {@code X-API-Key} header.
 *
 * <p>Registered directly in the security chain rather than as a servlet filter, so it only runs
 * for the versioned merchant order API. Keys are checked only through {@link ApiKeyApi}.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String MERCHANT_API_AUTHORITY = "ROLE_MERCHANT_API";

    private final ApiKeyApi apiKeyApi;
    private final JwtService jwtService;
    private final SecurityErrorHandler securityErrorHandler;

    public ApiKeyAuthenticationFilter(
            ApiKeyApi apiKeyApi,
            JwtService jwtService,
            SecurityErrorHandler securityErrorHandler) {
        this.apiKeyApi = apiKeyApi;
        this.jwtService = jwtService;
        this.securityErrorHandler = securityErrorHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthenticatedMerchant principal;
        try {
            principal = authenticate(request);
        } catch (ApiException ex) {
            securityErrorHandler.handle(request, response, ex);
            return;
        }
        if (principal == null) {
            reject(request, response);
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                java.util.List.of(new SimpleGrantedAuthority(MERCHANT_API_AUTHORITY)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private AuthenticatedMerchant authenticate(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKeyApi.validate(apiKey)
                    .map(key -> new AuthenticatedMerchant(key.merchantId(), key.apiCredentialId(), key.keyId()))
                    .orElse(null);
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        // The token's key may have been revoked, or its merchant suspended, since it was issued.
        return jwtService.parseMerchantApiToken(authorization.substring("Bearer ".length()))
                .flatMap(token -> apiKeyApi.validateCredential(token.merchantId(), token.apiCredentialId()))
                .map(key -> new AuthenticatedMerchant(key.merchantId(), key.apiCredentialId(), key.keyId()))
                .orElse(null);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.equals(ApiPaths.ORDERS) && !path.startsWith(ApiPaths.ORDERS + "/");
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        securityErrorHandler.commence(
                request,
                response,
                new BadCredentialsException("Invalid merchant API key"));
    }
}
