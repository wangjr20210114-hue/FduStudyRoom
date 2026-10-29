package com.campus.common.security;

import com.campus.common.error.ApiErrors;
import com.campus.common.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class CookieAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_PATHS = Set.of("/health", "/api/auth/config", "/api/auth/csrf",
            "/api/auth/dev/login", "/api/auth/fudan/login", "/api/auth/fudan/callback", "/internal/auth/introspect");
    private final TokenService tokens;
    private final AuthCookies cookies;
    private final IdentityLookup identities;
    private final ApiErrors errors;

    public CookieAuthenticationFilter(TokenService tokens, AuthCookies cookies, IdentityLookup identities, ApiErrors errors) {
        this.tokens = tokens; this.cookies = cookies; this.identities = identities; this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return PUBLIC_PATHS.contains(request.getServletPath()); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        try {
            String token = cookies.read(request);
            if (token != null) {
                var principal = identities.resolve(tokens.verify(token));
                var authorities = principal.permissions().stream().map(SimpleGrantedAuthority::new).toList();
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
                SecurityContextHolder.setContext(context);
            }
        } catch (ApiException error) {
            if (error.status() == 401) cookies.clear(response);
            errors.write(request, response, error.status(), error.code());
            return;
        } catch (RuntimeException error) {
            errors.write(request, response, 503, "IDENTITY_UNAVAILABLE");
            return;
        }
        chain.doFilter(request, response);
    }
}
