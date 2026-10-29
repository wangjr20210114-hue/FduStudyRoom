package com.campus.common.security;

public interface IdentityLookup {
    IdentityPrincipal resolve(TokenService.Claims claims);
}
