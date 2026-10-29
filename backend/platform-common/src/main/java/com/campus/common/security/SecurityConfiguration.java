package com.campus.common.security;

import com.campus.common.error.ApiErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(AuthSettings.class)
public class SecurityConfiguration {
    private final AuthSettings settings;

    public SecurityConfiguration(AuthSettings settings, Environment environment, @Value("${server.address:}") String address) {
        settings.validate(environment.getActiveProfiles(), address);
        this.settings = settings;
    }

    @Bean
    CookieCsrfTokenRepository csrfRepository() {
        var repository = new CookieCsrfTokenRepository();
        repository.setCookieName(settings.secureCookies() ? "__Host-FDU_CSRF" : "FDU_CSRF");
        repository.setCookieCustomizer(cookie -> cookie.path(settings.secureCookies() ? "/" : "/api").httpOnly(true).secure(settings.secureCookies()).sameSite("Strict"));
        return repository;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, CookieCsrfTokenRepository csrf, TokenService tokens,
            AuthCookies cookies, IdentityLookup identities, ApiErrors errors) throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(config -> config.csrfTokenRepository(csrf).ignoringRequestMatchers("/internal/auth/introspect"))
            .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll().requestMatchers("/health", "/api/auth/config", "/api/auth/csrf",
                    "/api/auth/login", "/api/auth/register", "/internal/auth/introspect").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(config -> config
                    .authenticationEntryPoint((request, response, error) -> errors.write(request, response, 401, "AUTHENTICATION_REQUIRED"))
                    .accessDeniedHandler((request, response, error) -> errors.write(request, response, 403,
                            error instanceof CsrfException ? "CSRF_INVALID" : "ACCESS_DENIED")))
            .addFilterBefore(new CookieAuthenticationFilter(tokens, cookies, identities, errors), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
