package com.campus.common.security;

import com.campus.common.error.ApiException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class TokenService {
    public record Claims(UUID userId, int authVersion) {}
    private static final String ISSUER = "fdu-study-room";
    private static final String AUDIENCE = "campus-api";
    private final AuthSettings settings;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    @Autowired
    public TokenService(AuthSettings settings) { this(settings, Clock.systemUTC()); }

    TokenService(AuthSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
        var key = new SecretKeySpec(Base64.getDecoder().decode(settings.signingKey()), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(ISSUER), jwt -> {
            boolean valid = jwt.getAudience().contains(AUDIENCE) && jwt.getExpiresAt() != null
                    && jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(clock.instant())
                    && settings.mode().equals(jwt.getClaimAsString("mode"));
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        }));
    }

    public String issue(UUID userId, int version) {
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(ISSUER).audience(java.util.List.of(AUDIENCE))
                .subject(userId.toString()).issuedAt(now).expiresAt(now.plus(settings.tokenTtl()))
                .id(UUID.randomUUID().toString()).claim("version", version).claim("mode", settings.mode()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public Claims verify(String token) {
        try {
            var jwt = decoder.decode(token);
            Object value = jwt.getClaim("version");
            if (!(value instanceof Number number) || number.intValue() <= 0 || number.doubleValue() != number.intValue()) {
                throw new IllegalArgumentException("Invalid version");
            }
            return new Claims(UUID.fromString(jwt.getSubject()), number.intValue());
        } catch (RuntimeException error) {
            throw new ApiException(401, "INVALID_SESSION");
        }
    }
}
