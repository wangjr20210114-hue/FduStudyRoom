package com.campus.identity.application;

import com.campus.common.error.ApiException;
import com.campus.common.security.*;
import com.campus.identity.domain.Account;
import com.campus.identity.infrastructure.AccountRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService implements IdentityLookup {
    private static final Logger LOG = LoggerFactory.getLogger(IdentityService.class);
    private final AccountRepository accounts;
    private final AuthSettings settings;
    public IdentityService(AccountRepository accounts, AuthSettings settings) { this.accounts = accounts; this.settings = settings; }

    @Override
    @Transactional(readOnly = true)
    public IdentityPrincipal resolve(TokenService.Claims claims) {
        var account = accounts.find(claims.userId()).orElseThrow(() -> new ApiException(401, "INVALID_SESSION"));
        if (account.authVersion() != claims.authVersion()) throw new ApiException(401, "INVALID_SESSION");
        account.requireEligible(settings.mode());
        return account.principal();
    }

    @Transactional(readOnly = true)
    public Account developmentLogin(UUID id) {
        if (!"dev".equals(settings.mode())) throw new ApiException(404, "NOT_FOUND");
        var account = accounts.find(id).orElseThrow(() -> new ApiException(403, "IDENTITY_INELIGIBLE"));
        try { account.requireEligible(settings.mode()); }
        catch (ApiException error) {
            LOG.info("security_event=login_denied user_id={} code={}", id, error.code());
            throw error;
        }
        LOG.info("security_event=development_login user_id={}", id);
        return account;
    }

    @Transactional
    public void logout(UUID id) {
        accounts.revoke(id);
        LOG.info("security_event=logout_all_sessions user_id={}", id);
    }

    @Transactional
    public void changeAccess(UUID actor, UUID id, UUID roleId, boolean enabled, int expectedVersion) {
        Account account = accounts.find(id).orElseThrow(() -> new ApiException(404, "USER_NOT_FOUND"));
        var role = accounts.roles().stream().filter(item -> item.id().equals(roleId) && item.enabled())
                .findFirst().orElseThrow(() -> new ApiException(400, "INVALID_ROLE"));
        if ("STAFF".equals(account.identityType()) && !"SYSTEM_ADMIN".equals(role.code()) && !"MAINTAINER".equals(role.code())) {
            throw new ApiException(400, "STAFF_ROLE_NOT_ALLOWED");
        }
        if (!accounts.updateAccess(id, roleId, enabled, expectedVersion)) throw new ApiException(409, "VERSION_CONFLICT");
        LOG.info("security_event=access_changed actor_id={} user_id={}", actor, id);
    }
}
