package com.campus.common.security;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthSettingsTest {
    private AuthSettings settings(String mode, boolean secure) {
        return new AuthSettings(mode, Base64.getEncoder().encodeToString(new byte[32]), "x".repeat(32), secure, Duration.ofMinutes(30));
    }
    @Test void developmentRequiresExplicitLocalProfile() {
        assertThatCode(() -> settings("dev", false).validate(new String[]{"local"}, "127.0.0.1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> settings("dev", false).validate(new String[]{}, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> settings("dev", false).validate(new String[]{"local","prod"}, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
    }
    @Test void developmentCannotBindPublicInterfaces() {
        assertThatThrownBy(() -> settings("dev", false).validate(new String[]{"local"}, "0.0.0.0")).isInstanceOf(IllegalStateException.class);
    }
    @Test void uisRequiresSecureCookies() {
        assertThatThrownBy(() -> settings("uis", false).validate(new String[]{"prod"}, "0.0.0.0")).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> settings("uis", true).validate(new String[]{"prod"}, "0.0.0.0")).doesNotThrowAnyException();
    }
    @Test void missingOrWeakSecretsAreRejected() {
        var weak = new AuthSettings("uis", "", "", true, Duration.ofMinutes(30));
        assertThatThrownBy(() -> weak.validate(new String[]{"prod"}, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
    }
    @Test void lifetimeIsBounded() {
        var settings = new AuthSettings("uis", Base64.getEncoder().encodeToString(new byte[32]), "x".repeat(32), true, Duration.ofHours(3));
        assertThatThrownBy(() -> settings.validate(new String[]{"prod"}, "127.0.0.1")).isInstanceOf(IllegalStateException.class);
    }
}
