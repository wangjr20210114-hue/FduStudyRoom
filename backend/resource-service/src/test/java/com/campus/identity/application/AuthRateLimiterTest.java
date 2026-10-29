package com.campus.identity.application;

import com.campus.common.error.ApiException;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthRateLimiterTest {
    @Test void limitsAttemptsAndAllowsRequestsAfterTheWindowExpires() {
        var instant = new AtomicReference<>(Instant.parse("2026-10-29T00:00:00Z"));
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return instant.get(); }
        };
        var limiter = new AuthRateLimiter(clock);
        limiter.check("account:test",2,Duration.ofMinutes(1));
        limiter.check("account:test",2,Duration.ofMinutes(1));
        assertThatThrownBy(() -> limiter.check("account:test",2,Duration.ofMinutes(1))).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.status()).isEqualTo(429));
        assertThatCode(() -> limiter.check("account:another",2,Duration.ofMinutes(1))).doesNotThrowAnyException();
        instant.set(instant.get().plusSeconds(60));
        assertThatCode(() -> limiter.check("account:test",2,Duration.ofMinutes(1))).doesNotThrowAnyException();
    }
}
