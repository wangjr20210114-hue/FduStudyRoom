package com.campus.identity.application;

import com.campus.common.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
    private record Window(int count, Instant until) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public AuthRateLimiter() { this(Clock.systemUTC()); }
    AuthRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized void check(String key, int limit, Duration duration) {
        Instant now = clock.instant();
        Window window = windows.get(key);
        if (window == null || !window.until().isAfter(now)) {
            if (windows.size() >= 10000) windows.entrySet().removeIf(entry -> !entry.getValue().until().isAfter(now));
            if (windows.size() >= 10000 && !windows.containsKey(key)) throw new ApiException(429, "RATE_LIMITED");
            windows.put(key, new Window(1, now.plus(duration)));
        } else {
            if (window.count() >= limit) throw new ApiException(429, "RATE_LIMITED");
            windows.put(key, new Window(window.count() + 1, window.until()));
        }
    }
}
