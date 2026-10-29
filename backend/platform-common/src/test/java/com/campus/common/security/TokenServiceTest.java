package com.campus.common.security;

import com.campus.common.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TokenServiceTest {
    private final Instant now = Instant.parse("2026-10-26T00:00:00Z");
    private final UUID id = UUID.randomUUID();
    private AuthSettings settings(String mode, byte keyByte) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, keyByte);
        return new AuthSettings(mode, Base64.getEncoder().encodeToString(key), "a".repeat(32), true, Duration.ofMinutes(30));
    }
    private TokenService service(String mode, byte key, Instant time) {
        return new TokenService(settings(mode, key), Clock.fixed(time, ZoneOffset.UTC));
    }
    @Test void signedTokenPreservesSubjectAndVersion() {
        var tokens = service("dev", (byte)1, now);
        assertThat(tokens.verify(tokens.issue(id, 3))).isEqualTo(new TokenService.Claims(id, 3));
    }
    @Test void expiredTokenIsRejected() {
        String token = service("dev", (byte)1, now).issue(id, 1);
        assertThatThrownBy(() -> service("dev", (byte)1, now.plusSeconds(1801)).verify(token)).isInstanceOf(ApiException.class);
    }
    @Test void anotherSigningKeyCannotAuthenticateTheToken() {
        String token = service("dev", (byte)1, now).issue(id, 1);
        assertThatThrownBy(() -> service("dev", (byte)2, now).verify(token)).isInstanceOf(ApiException.class);
    }
    @Test void developmentTokenCannotBeUsedInUisModeWithTheSameKey() {
        String token = service("dev", (byte)1, now).issue(id, 1);
        assertThatThrownBy(() -> service("uis", (byte)1, now).verify(token)).isInstanceOf(ApiException.class);
    }
    @Test void unsignedOrMalformedTokensAreRejected() {
        for (String token : new String[]{"not-a-token", "eyJhbGciOiJub25lIn0.e30.", ""}) {
            assertThatThrownBy(() -> service("dev", (byte)1, now).verify(token)).isInstanceOf(ApiException.class);
        }
    }
    @Test void futureIssuedTokenIsRejected() {
        String token = service("dev", (byte)1, now.plusSeconds(60)).issue(id, 1);
        assertThatThrownBy(() -> service("dev", (byte)1, now).verify(token)).isInstanceOf(ApiException.class);
    }
}
