package com.campus.common.security;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("campus.auth")
public record AuthSettings(String mode, String signingKey, String serviceKey, boolean secureCookies, Duration tokenTtl) {
    public void validate(String[] profiles, String address) {
        if (!"dev".equals(mode) && !"uis".equals(mode)) throw new IllegalStateException("Authentication mode must be dev or uis");
        byte[] key;
        try { key = Base64.getDecoder().decode(signingKey == null ? "" : signingKey); }
        catch (IllegalArgumentException error) { throw new IllegalStateException("Authentication signing key must be Base64"); }
        if (key.length < 32) throw new IllegalStateException("Authentication signing key requires at least 32 random bytes");
        if (serviceKey == null || serviceKey.length() < 32) throw new IllegalStateException("Internal service key requires at least 32 characters");
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative() || tokenTtl.compareTo(Duration.ofHours(2)) > 0) {
            throw new IllegalStateException("Authentication lifetime must be positive and at most two hours");
        }
        if ("dev".equals(mode)) {
            if (!Arrays.equals(profiles, new String[]{"local"}) || !("127.0.0.1".equals(address) || "::1".equals(address))) {
                throw new IllegalStateException("Development authentication requires only the local profile and loopback binding");
            }
        } else if (!secureCookies) {
            throw new IllegalStateException("UIS mode requires secure cookies");
        }
    }
}
