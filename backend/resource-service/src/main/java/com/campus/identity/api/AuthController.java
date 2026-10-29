package com.campus.identity.api;

import com.campus.common.error.ApiException;
import com.campus.common.security.*;
import com.campus.identity.application.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
    private final AuthSettings settings;
    private final IdentityService identities;
    private final TokenService tokens;
    private final AuthCookies cookies;
    private final CookieCsrfTokenRepository csrf;

    public AuthController(AuthSettings settings, IdentityService identities, TokenService tokens, AuthCookies cookies, CookieCsrfTokenRepository csrf) {
        this.settings = settings; this.identities = identities; this.tokens = tokens; this.cookies = cookies; this.csrf = csrf;
    }

    @GetMapping("/health")
    Map<String, String> health() { return Map.of("status", "UP"); }

    @GetMapping("/api/auth/config")
    Map<String, Object> config() {
        boolean development = "dev".equals(settings.mode());
        return Map.of("mode", development ? "development" : "fudan", "available", development,
                "developmentAccounts", development ? List.of(
                        Map.of("key", "student", "label", "开发学生"),
                        Map.of("key", "admin", "label", "开发管理员"),
                        Map.of("key", "maintainer", "label", "开发维护人员")) : List.of(),
                "unavailableCode", development ? "" : "UIS_NOT_CONFIGURED");
    }

    public record CsrfResponse(String headerName, String token) {}
    @GetMapping("/api/auth/csrf")
    CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @GetMapping("/api/auth/me")
    IdentityPrincipal me(@AuthenticationPrincipal IdentityPrincipal principal) { return principal; }

    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal IdentityPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        identities.logout(principal.id());
        cookies.clear(response);
        csrf.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/api/auth/fudan/login", "/api/auth/fudan/callback"})
    void fudan() { throw new ApiException(503, "UIS_NOT_CONFIGURED"); }

    @PostMapping("/internal/auth/introspect")
    IdentityPrincipal introspect(@RequestHeader(value = "X-Service-Key", required = false) String serviceKey,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (serviceKey == null || !MessageDigest.isEqual(settings.serviceKey().getBytes(StandardCharsets.UTF_8),
                serviceKey.getBytes(StandardCharsets.UTF_8))) throw new ApiException(401, "SERVICE_AUTHENTICATION_REQUIRED");
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new ApiException(401, "INVALID_SESSION");
        return identities.resolve(tokens.verify(authorization.substring(7)));
    }
}
