package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import org.springframework.http.ResponseCookie;

/**
 * Cookie refresh token dùng chung cho đăng nhập và xoay token. Ghi nhớ đăng nhập thì cookie bền theo
 * thời hạn refresh token; không ghi nhớ thì không đặt Max-Age nên là cookie phiên, hết khi đóng trình duyệt.
 */
final class RefreshTokenCookie {
    private RefreshTokenCookie() { }

    static ResponseCookie issue(SessionSecurityProperties properties, String value, boolean rememberMe) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path("/api/auth");
        if (rememberMe) {
            cookie.maxAge(properties.refreshTokenSeconds());
        }
        return cookie.build();
    }
}
