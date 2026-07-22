package org.psint.beyosclothing.core.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cookie Utility for managing HTTP-only authentication cookies
 * Sets cookies with proper security attributes for production
 */
@Component
@Slf4j
public class CookieUtil {

    @Value("${app.cookie.domain}")
    private String cookieDomain;

    @Value("${app.cookie.secure:false}")
    private boolean secure;

    private static final boolean HTTP_ONLY = true;
    private static final String SAME_SITE = "Lax";

    /**
     * Set access token cookie
     */
    public void setAccessTokenCookie(HttpServletResponse response, String token, int maxAgeSeconds) {
        Cookie cookie = new Cookie("access_token", token);
        cookie.setHttpOnly(HTTP_ONLY);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setDomain(cookieDomain);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", SAME_SITE);
        response.addCookie(cookie);
        log.debug("Access token cookie set with maxAge: {} seconds, domain: {}", maxAgeSeconds, cookieDomain);
    }

    /**
     * Set refresh token cookie
     */
    public void setRefreshTokenCookie(HttpServletResponse response, String token, int maxAgeSeconds) {
        Cookie cookie = new Cookie("refresh_token", token);
        cookie.setHttpOnly(HTTP_ONLY);
        cookie.setSecure(secure);
        cookie.setPath("/api/v1/auth/refresh");
        cookie.setDomain(cookieDomain);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", SAME_SITE);
        response.addCookie(cookie);
        log.debug("Refresh token cookie set with maxAge: {} seconds, domain: {}", maxAgeSeconds, cookieDomain);
    }

    /**
     * Clear access token cookie
     */
    public void clearAccessTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("access_token", null);
        cookie.setHttpOnly(HTTP_ONLY);
        cookie.setSecure(secure);
        cookie.setPath("/");
        cookie.setDomain(cookieDomain);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        log.debug("Access token cookie cleared, domain: {}", cookieDomain);
    }

    /**
     * Clear refresh token cookie
     */
    public void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refresh_token", null);
        cookie.setHttpOnly(HTTP_ONLY);
        cookie.setSecure(secure);
        cookie.setPath("/api/v1/auth/refresh");
        cookie.setDomain(cookieDomain);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        log.debug("Refresh token cookie cleared, domain: {}", cookieDomain);
    }

    /**
     * Clear all auth cookies
     */
    public void clearAllAuthCookies(HttpServletResponse response) {
        clearAccessTokenCookie(response);
        clearRefreshTokenCookie(response);
    }
}
