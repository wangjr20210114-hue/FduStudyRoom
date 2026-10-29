package com.campus.common.security;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthSettingsTest {
    private AuthSettings settings(String mode, boolean secure) {
        return new AuthSettings(mode, Base64.getEncoder().encodeToString(new byte[32]), "x".repeat(32), secure, Duration.ofMinutes(30));
    }
    @Test void localHttpRequiresExplicitLocalProfileAndLoopback() {
        assertThatCode(() -> settings("password", false).validate(new String[]{"local"}, "127.0.0.1")).doesNotThrowAnyException();
        for (String[] profiles : new String[][]{ {}, {"local","prod"}, {"prod"} }) {
            assertThatThrownBy(() -> settings("password", false).validate(profiles, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> settings("password", false).validate(new String[]{"local"}, "0.0.0.0")).isInstanceOf(IllegalStateException.class);
    }
    @Test void productionUsesSecureCookies() {
        assertThatCode(() -> settings("password", true).validate(new String[]{"prod"}, "0.0.0.0")).doesNotThrowAnyException();
    }
    @Test void legacyModesCannotStart() {
        for (String mode : new String[]{"dev","uis"}) assertThatThrownBy(() -> settings(mode,true).validate(new String[]{"prod"}, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
    }
    @Test void missingOrWeakSecretsAreRejected() {
        var weak = new AuthSettings("password", "", "", true, Duration.ofMinutes(30));
        assertThatThrownBy(() -> weak.validate(new String[]{"prod"}, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
    }
    @Test void lifetimeIsBounded() {
        var settings = new AuthSettings("password", Base64.getEncoder().encodeToString(new byte[32]), "x".repeat(32), true, Duration.ofHours(3));
        assertThatThrownBy(() -> settings.validate(new String[]{"prod"}, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
    }
}
