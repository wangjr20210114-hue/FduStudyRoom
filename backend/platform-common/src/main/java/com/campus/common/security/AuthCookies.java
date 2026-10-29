package com.campus.common.security;

import com.campus.common.error.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookies {
    private final AuthSettings settings;
    public AuthCookies(AuthSettings settings) { this.settings = settings; }
    public String name() { return settings.secureCookies() ? "__Host-FDU_SESSION" : "FDU_SESSION"; }
    public String read(HttpServletRequest request) {
        String token = null;
        for (Cookie cookie : request.getCookies() == null ? new Cookie[0] : request.getCookies()) {
            if (name().equals(cookie.getName())) {
                if (token != null) throw new ApiException(401, "INVALID_SESSION");
                token = cookie.getValue();
            }
        }
        return token;
    }
    public void write(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name(), token).httpOnly(true)
                .secure(settings.secureCookies()).sameSite("Lax").path(settings.secureCookies() ? "/" : "/api").maxAge(settings.tokenTtl()).build().toString());
    }
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name(), "").httpOnly(true)
                .secure(settings.secureCookies()).sameSite("Lax").path(settings.secureCookies() ? "/" : "/api").maxAge(0).build().toString());
    }
}
