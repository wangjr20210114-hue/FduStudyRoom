package com.campus.identity.api;

import com.campus.common.error.ApiException;
import com.campus.common.security.AuthCookies;
import com.campus.common.security.IdentityPrincipal;
import com.campus.common.security.TokenService;
import com.campus.identity.application.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")
@ConditionalOnProperty(name = "campus.auth.mode", havingValue = "dev")
public class DevelopmentLoginController {
    private static final Map<String, UUID> ACCOUNTS = Map.of(
            "student", UUID.fromString("00000000-0000-0000-0000-000000002001"),
            "admin", UUID.fromString("00000000-0000-0000-0000-000000002002"),
            "maintainer", UUID.fromString("00000000-0000-0000-0000-000000002003"),
            "staff-user", UUID.fromString("00000000-0000-0000-0000-000000002004"),
            "inactive", UUID.fromString("00000000-0000-0000-0000-000000002005"),
            "disabled", UUID.fromString("00000000-0000-0000-0000-000000002006"));
    private final IdentityService identities;
    private final TokenService tokens;
    private final AuthCookies cookies;
    private final CookieCsrfTokenRepository csrf;

    public DevelopmentLoginController(IdentityService identities, TokenService tokens, AuthCookies cookies, CookieCsrfTokenRepository csrf) {
        this.identities = identities; this.tokens = tokens; this.cookies = cookies; this.csrf = csrf;
    }

    public record LoginRequest(@NotBlank String account) {}
    @PostMapping("/api/auth/dev/login")
    IdentityPrincipal login(@Valid @RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        if (!java.net.InetAddress.getLoopbackAddress().getHostAddress().equals(request.getRemoteAddr())
                && !"127.0.0.1".equals(request.getRemoteAddr()) && !"::1".equals(request.getRemoteAddr())
                && !"0:0:0:0:0:0:0:1".equals(request.getRemoteAddr())) {
            throw new ApiException(403, "DEVELOPMENT_LOGIN_LOCAL_ONLY");
        }
        UUID id = ACCOUNTS.get(input.account());
        if (id == null) throw new ApiException(400, "UNKNOWN_DEVELOPMENT_ACCOUNT");
        var account = identities.developmentLogin(id);
        cookies.write(response, tokens.issue(account.id(), account.authVersion()));
        csrf.saveToken(null, request, response);
        return account.principal();
    }
}
