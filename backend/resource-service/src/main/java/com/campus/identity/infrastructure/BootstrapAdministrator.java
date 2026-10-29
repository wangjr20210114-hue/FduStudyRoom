package com.campus.identity.infrastructure;

import com.campus.identity.application.IdentityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdministrator implements ApplicationRunner {
    private final IdentityService identities;
    private final String loginName;
    private final String password;
    public BootstrapAdministrator(IdentityService identities,
            @Value("${campus.bootstrap-admin.login-name:}") String loginName,
            @Value("${campus.bootstrap-admin.password:}") String password) {
        this.identities = identities; this.loginName = loginName; this.password = password;
    }
    @Override
    public void run(ApplicationArguments args) {
        if (loginName.isBlank() && password.isBlank()) return;
        if (loginName.isBlank() || password.isBlank()) throw new IllegalStateException("Both bootstrap administrator values are required.");
        identities.bootstrapAdmin(loginName, password);
    }
}
