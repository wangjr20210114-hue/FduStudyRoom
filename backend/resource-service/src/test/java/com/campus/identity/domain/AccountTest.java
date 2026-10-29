package com.campus.identity.domain;

import com.campus.common.error.ApiException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AccountTest {
    private Account account(String role, boolean enabled, boolean roleEnabled) {
        return new Account(UUID.randomUUID(), "test", "Test", null, UUID.randomUUID(), role, roleEnabled, enabled, 1, Set.of("identity:read"));
    }
    @Test void ordinaryAndAssignedRolesDoNotRequireSchoolVerification() {
        for (String role : new String[]{"USER","SYSTEM_ADMIN","MAINTAINER"}) {
            assertThatCode(() -> account(role,true,true).requireEligible()).doesNotThrowAnyException();
        }
    }
    @Test void disabledAccountOrRoleIsRejected() {
        assertThatThrownBy(() -> account("USER",false,true).requireEligible()).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> account("USER",true,false).requireEligible()).isInstanceOf(ApiException.class);
    }
    @Test void principalContainsNoCredentials() {
        assertThat(account("USER",true,true).principal().toString()).doesNotContain("password");
    }
}
