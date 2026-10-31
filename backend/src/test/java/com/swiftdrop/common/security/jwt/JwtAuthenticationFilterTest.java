package com.swiftdrop.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.Set;
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
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;
    @Mock private FilterChain chain;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void filter() throws Exception {
        new JwtAuthenticationFilter(jwtService).doFilter(request, response, chain);
    }

    @Test
    void validBearerTokenSignsTheRequestIn() throws Exception {
        request.addHeader("Authorization", "Bearer good-token");
        AuthenticatedUser user = new AuthenticatedUser("user-1", Set.of(Role.COURIER));
        when(jwtService.parse("good-token")).thenReturn(Optional.of(user));

        filter();

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(user);
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_COURIER");
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidTokenLeavesTheRequestAnonymous() throws Exception {
        request.addHeader("Authorization", "Bearer forged");
        when(jwtService.parse("forged")).thenReturn(Optional.empty());

        filter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response); // the security chain answers 401 if the route needs it
    }

    @Test
    void requestsWithoutABearerTokenAreNotParsed() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter();

        verifyNoInteractions(jwtService);
        verify(chain).doFilter(request, response);
    }
}
