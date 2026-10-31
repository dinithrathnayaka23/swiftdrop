package com.swiftdrop.identity.security;

import com.swiftdrop.common.api.ApiPaths;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * The refresh token cookie. httpOnly (page scripts, and so XSS, can't read it), SameSite=Strict
 * (never sent by other sites, which blocks CSRF) and scoped to /api/v1/auth (not sent with any
 * other request).
 */
@Component
public class SessionCookies {

    private final String name;
    private final boolean secure;

    public SessionCookies(@Value("${swiftdrop.auth.refresh-token.cookie-name}") String name,
                          @Value("${swiftdrop.auth.refresh-token.cookie-secure}") boolean secure) {
        this.name = name;
        this.secure = secure;
    }

    /**
     * @param rememberMe true: kept for {@code lifetime}, across browser restarts. false: a browser
     *                   session cookie, forgotten when the browser closes.
     */
    public String issue(String refreshToken, boolean rememberMe, Duration lifetime) {
        var cookie = base(refreshToken);
        if (rememberMe) {
            cookie.maxAge(lifetime);
        }
        return cookie.build().toString();
    }

    /** The refresh token sent by the browser, or null if there is none. */
    public String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, name);
        return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
    }

    public String clear() {
        return base("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(ApiPaths.AUTH);
    }
}
