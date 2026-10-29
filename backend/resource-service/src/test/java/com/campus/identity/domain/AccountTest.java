package com.campus.identity.domain;

import com.campus.common.error.ApiException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AccountTest {
    private Account account(String type, String role, String source, boolean active, boolean enabled, boolean roleEnabled) {
        return new Account(UUID.randomUUID(), "DEV-TEST", "Development", null, type, source, active,
                UUID.randomUUID(), role, roleEnabled, enabled, 1, Set.of("identity:read"));
    }
    @Test void activeStudentCanUseTheOrdinaryRole() {
        assertThatCode(() -> account("STUDENT","USER","DEVELOPMENT",true,true,true).requireEligible("dev")).doesNotThrowAnyException();
    }
    @Test void staffNeedsAnAssignedManagementOrMaintenanceRole() {
        for (String role : new String[]{"SYSTEM_ADMIN","MAINTAINER"}) {
            assertThatCode(() -> account("STAFF",role,"DEVELOPMENT",true,true,true).requireEligible("dev")).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> account("STAFF","USER","DEVELOPMENT",true,true,true).requireEligible("dev")).isInstanceOf(ApiException.class);
    }
    @Test void inactiveIdentityOrDisabledAccountOrRoleIsRejected() {
        for (Account account : new Account[]{
                account("STUDENT","USER","DEVELOPMENT",false,true,true),
                account("STUDENT","USER","DEVELOPMENT",true,false,true),
                account("STUDENT","USER","DEVELOPMENT",true,true,false)}) {
            assertThatThrownBy(() -> account.requireEligible("dev")).isInstanceOf(ApiException.class);
        }
    }
    @Test void developmentDatabaseIdentitiesAreRejectedInUisMode() {
        assertThatThrownBy(() -> account("STUDENT","USER","DEVELOPMENT",true,true,true).requireEligible("uis")).isInstanceOf(ApiException.class);
    }
}
