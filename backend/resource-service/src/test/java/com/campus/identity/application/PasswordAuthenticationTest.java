package com.campus.identity.application;

import com.campus.common.error.ApiException;
import com.campus.identity.domain.Account;
import com.campus.identity.infrastructure.AccountRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordAuthenticationTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final org.springframework.security.crypto.password.PasswordEncoder encoder = new PasswordConfiguration().passwordEncoder();

    @Test void passwordHashUsesRandomSaltAndRejectsWrongPassword() {
        String first = encoder.encode("test-password-123");
        String second = encoder.encode("test-password-123");
        assertThat(first).startsWith("{pbkdf2-sha256-v1}").isNotEqualTo(second).doesNotContain("test-password");
        assertThat(encoder.matches("test-password-123",first)).isTrue();
        assertThat(encoder.matches("wrong-password",first)).isFalse();
    }
    @Test void publicRegistrationAlwaysUsesTheOrdinaryRole() {
        var account = new Account(UUID.randomUUID(),"new-user","new-user",null,UUID.randomUUID(),"USER",true,true,1,Set.of("identity:read"));
        when(accounts.create(eq("new-user"),anyString(),eq("USER"))).thenReturn(account);
        new IdentityService(accounts,encoder).register(" NEW-USER ","test-password-123");
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(accounts).create(eq("new-user"),hash.capture(),eq("USER"));
        assertThat(encoder.matches("test-password-123",hash.getValue())).isTrue();
    }
    @Test void unknownOrDisabledAccountsHaveTheSameFailure() {
        var identities = new IdentityService(accounts,encoder);
        when(accounts.credentials("unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> identities.login("unknown","test-password-123")).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("INVALID_CREDENTIALS"));
        UUID id=UUID.randomUUID();
        when(accounts.credentials("disabled")).thenReturn(Optional.of(new AccountRepository.Credentials(id,encoder.encode("test-password-123"))));
        when(accounts.find(id)).thenReturn(Optional.of(new Account(id,"disabled","Disabled",null,UUID.randomUUID(),"USER",true,false,1,Set.of())));
        assertThatThrownBy(() -> identities.login("disabled","test-password-123")).isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("INVALID_CREDENTIALS"));
    }
    @Test void invalidPasswordsAreRejectedBeforeDatabaseAccessWithoutEchoingTheValue() {
        var identities = new IdentityService(accounts,encoder);
        for (String password : new String[]{null, "", "short", " ".repeat(8), "x".repeat(65)}) {
            assertThatThrownBy(() -> identities.register("new-user",password))
                    .isInstanceOfSatisfying(ApiException.class,e -> {
                        assertThat(e.code()).isEqualTo("INVALID_PASSWORD");
                        assertThat(e.status()).isEqualTo(400);
                    });
            assertThatThrownBy(() -> identities.login("new-user",password))
                    .isInstanceOfSatisfying(ApiException.class,e -> assertThat(e.code()).isEqualTo("INVALID_PASSWORD"));
        }
        verifyNoInteractions(accounts);
    }
    @Test void loginNamesAreNormalizedAndBounded() {
        assertThat(IdentityService.normalizeLoginName(" My.User ")).isEqualTo("my.user");
        for (String name : new String[]{"x","../admin","bad user","a".repeat(33)}) assertThatThrownBy(() -> IdentityService.normalizeLoginName(name)).isInstanceOf(ApiException.class);
    }
    @Test void bootstrapNeverPromotesAnExistingOrdinaryUser() {
        UUID id=UUID.randomUUID();
        when(accounts.credentials("admin")).thenReturn(Optional.of(new AccountRepository.Credentials(id,"existing")));
        when(accounts.find(id)).thenReturn(Optional.of(new Account(id,"admin","Admin",null,UUID.randomUUID(),"USER",true,true,1,Set.of())));
        assertThatThrownBy(() -> new IdentityService(accounts,encoder).bootstrapAdmin("admin","bootstrap-password-123")).isInstanceOf(IllegalStateException.class);
        verify(accounts,never()).create(anyString(),anyString(),anyString());
    }
}
