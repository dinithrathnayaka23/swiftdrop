package com.swiftdrop.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SessionCookiesTest {

    private final SessionCookies cookies = new SessionCookies("swiftdrop_session", true);

    @Test
    void sessionCookieIsHiddenFromScriptsAndOtherSites() {
        String cookie = cookies.issue("refresh-jwt", false, Duration.ofDays(1));

        assertThat(cookie).startsWith("swiftdrop_session=refresh-jwt")
                .contains("Path=/api/v1/auth", "HttpOnly", "Secure", "SameSite=Strict")
                .doesNotContain("Max-Age"); // forgotten when the browser closes
    }

    @Test
    void rememberMeCookieSurvivesBrowserRestarts() {
        assertThat(cookies.issue("refresh-jwt", true, Duration.ofDays(30))).contains("Max-Age=2592000");
    }

    @Test
    void clearingExpiresTheCookieImmediately() {
        assertThat(cookies.clear()).startsWith("swiftdrop_session=;").contains("Max-Age=0", "Path=/api/v1/auth");
    }

    @Test
    void readsOnlyItsOwnNonEmptyCookie() {
        var request = new MockHttpServletRequest();
        assertThat(cookies.read(request)).isNull();

        request.setCookies(new Cookie("other", "x"),
                new Cookie("swiftdrop_session", "refresh-jwt"));
        assertThat(cookies.read(request)).isEqualTo("refresh-jwt");

        request.setCookies(new Cookie("swiftdrop_session", ""));
        assertThat(cookies.read(request)).isNull();
    }

    @Test
    void secureFlagCanBeTurnedOffForPlainHttpHosts() {
        assertThat(new SessionCookies("swiftdrop_session", false).issue("t", false, Duration.ofDays(1)))
                .doesNotContain("Secure");
    }
}
