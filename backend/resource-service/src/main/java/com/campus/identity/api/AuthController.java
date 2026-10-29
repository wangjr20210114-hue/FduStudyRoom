package com.campus.identity.api;

import com.campus.common.error.ApiException;
import com.campus.common.security.*;
import com.campus.identity.application.AuthRateLimiter;
import com.campus.identity.application.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
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
    private final AuthRateLimiter limiter;

    public AuthController(AuthSettings settings, IdentityService identities, TokenService tokens, AuthCookies cookies,
            CookieCsrfTokenRepository csrf, AuthRateLimiter limiter) {
        this.settings = settings; this.identities = identities; this.tokens = tokens;
        this.cookies = cookies; this.csrf = csrf; this.limiter = limiter;
    }

    @GetMapping("/health")
    Map<String, String> health() { return Map.of("status", "UP"); }

    @GetMapping("/api/auth/config")
    Map<String, Object> config() {
        return Map.of("mode", "password", "available", true, "registrationOpen", true);
    }

    public record CredentialsRequest(@NotBlank @Size(max=128) String loginName,
            String password) {
        @Override public String toString() { return "CredentialsRequest[REDACTED]"; }
    }

    @PostMapping("/api/auth/login")
    IdentityPrincipal login(@Valid @RequestBody CredentialsRequest input, HttpServletRequest request, HttpServletResponse response) {
        String name = IdentityService.normalizeLoginName(input.loginName());
        limiter.check("login:ip:" + request.getRemoteAddr(), 50, Duration.ofMinutes(1));
        limiter.check("login:account:" + name, 10, Duration.ofMinutes(1));
        var account = identities.login(name, input.password());
        establishSession(account.id(), account.authVersion(), request, response);
        return account.principal();
    }

    @PostMapping("/api/auth/register")
    ResponseEntity<IdentityPrincipal> register(@Valid @RequestBody CredentialsRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        String name = IdentityService.normalizeLoginName(input.loginName());
        limiter.check("register:ip:" + request.getRemoteAddr(), 20, Duration.ofMinutes(10));
        var account = identities.register(name, input.password());
        establishSession(account.id(), account.authVersion(), request, response);
        return ResponseEntity.status(201).body(account.principal());
    }

    private void establishSession(java.util.UUID id, int version, HttpServletRequest request, HttpServletResponse response) {
        cookies.write(response, tokens.issue(id, version));
        csrf.saveToken(null, request, response);
    }

    public record CsrfResponse(String headerName, String token) {}
    @GetMapping("/api/auth/csrf")
    CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @GetMapping("/api/auth/me")
    IdentityPrincipal me(@AuthenticationPrincipal IdentityPrincipal principal) { return principal; }

    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal IdentityPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        identities.logout(principal.id()); cookies.clear(response); csrf.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/auth/introspect")
    IdentityPrincipal introspect(@RequestHeader(value = "X-Service-Key", required = false) String serviceKey,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (serviceKey == null || !MessageDigest.isEqual(settings.serviceKey().getBytes(StandardCharsets.UTF_8),
                serviceKey.getBytes(StandardCharsets.UTF_8))) throw new ApiException(401, "SERVICE_AUTHENTICATION_REQUIRED");
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new ApiException(401, "INVALID_SESSION");
        return identities.resolve(tokens.verify(authorization.substring(7)));
    }
}
