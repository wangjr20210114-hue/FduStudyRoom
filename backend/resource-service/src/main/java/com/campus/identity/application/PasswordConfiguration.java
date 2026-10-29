package com.campus.identity.application;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        var encoder = new Pbkdf2PasswordEncoder("", 16, 600000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        return new DelegatingPasswordEncoder("pbkdf2-sha256-v1", Map.of("pbkdf2-sha256-v1", encoder));
    }
}
