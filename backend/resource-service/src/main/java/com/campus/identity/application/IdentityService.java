package com.campus.identity.application;

import com.campus.common.error.ApiException;
import com.campus.common.security.*;
import com.campus.identity.domain.Account;
import com.campus.identity.infrastructure.AccountRepository;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService implements IdentityLookup {
    private static final Logger LOG = LoggerFactory.getLogger(IdentityService.class);
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final String dummyHash;
    public IdentityService(AccountRepository accounts, PasswordEncoder passwords) {
        this.accounts = accounts; this.passwords = passwords;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public static String normalizeLoginName(String value) {
        String name = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9][a-z0-9_.-]{2,31}")) throw new ApiException(400, "INVALID_LOGIN_NAME");
        return name;
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8 || password.length() > 64) {
            throw new ApiException(400, "INVALID_PASSWORD");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public IdentityPrincipal resolve(TokenService.Claims claims) {
        var account = accounts.find(claims.userId()).orElseThrow(() -> new ApiException(401, "INVALID_SESSION"));
        if (account.authVersion() != claims.authVersion()) throw new ApiException(401, "INVALID_SESSION");
        account.requireEligible();
        return account.principal();
    }

    @Transactional(readOnly = true)
    public Account login(String loginName, String password) {
        validatePassword(password);
        var credentials = accounts.credentials(normalizeLoginName(loginName));
        String hash = credentials.map(AccountRepository.Credentials::passwordHash).orElse(dummyHash);
        boolean valid = passwords.matches(password, hash);
        if (!valid || credentials.isEmpty() || credentials.get().passwordHash() == null) throw new ApiException(401, "INVALID_CREDENTIALS");
        var account = accounts.find(credentials.get().id()).orElseThrow(() -> new ApiException(401, "INVALID_CREDENTIALS"));
        if (!account.enabled() || !account.roleEnabled()) throw new ApiException(401, "INVALID_CREDENTIALS");
        LOG.info("security_event=password_login user_id={}", account.id());
        return account;
    }

    @Transactional
    public Account register(String loginName, String password) {
        validatePassword(password);
        var account = accounts.create(normalizeLoginName(loginName), passwords.encode(password), "USER");
        LOG.info("security_event=registered user_id={}", account.id());
        return account;
    }

    @Transactional
    public void bootstrapAdmin(String loginName, String password) {
        String name = normalizeLoginName(loginName);
        var existing = accounts.credentials(name);
        if (existing.isPresent()) {
            var account = accounts.find(existing.get().id()).orElseThrow();
            if (!"SYSTEM_ADMIN".equals(account.roleCode()) || existing.get().passwordHash() == null) {
                throw new IllegalStateException("Bootstrap login is already assigned; choose an unused administrator login.");
            }
            return;
        }
        if (password == null || password.length() < 12 || password.length() > 64) {
            throw new IllegalStateException("Bootstrap administrator password must contain 12 to 64 characters.");
        }
        var account = accounts.create(name, passwords.encode(password), "SYSTEM_ADMIN");
        LOG.info("security_event=administrator_bootstrapped user_id={}", account.id());
    }

    @Transactional
    public void logout(UUID id) {
        accounts.revoke(id);
        LOG.info("security_event=logout_all_sessions user_id={}", id);
    }

    @Transactional
    public void changeAccess(UUID actor, UUID id, UUID roleId, boolean enabled, int expectedVersion) {
        accounts.find(id).orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND"));
        accounts.roles().stream().filter(item -> item.id().equals(roleId) && item.enabled())
                .findFirst().orElseThrow(() -> new ApiException(400, "INVALID_ROLE"));
        if (!accounts.updateAccess(id, roleId, enabled, expectedVersion)) throw new ApiException(409, "VERSION_CONFLICT");
        LOG.info("security_event=access_changed actor_id={} user_id={}", actor, id);
    }
}
