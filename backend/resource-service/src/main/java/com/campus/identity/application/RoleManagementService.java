package com.campus.identity.application;

import com.campus.identity.infrastructure.RoleRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {
    private final RoleRepository roles;
    public RoleManagementService(RoleRepository roles) { this.roles=roles; }
    @Transactional public RoleRepository.RoleDetails create(String code,String name,Set<String> permissions) { return roles.create(code,name.trim(),permissions); }
    @Transactional public void replacePermissions(UUID id,Set<String> permissions) { roles.replacePermissions(id,permissions); }
    @Transactional public void setEnabled(UUID id,boolean enabled) { roles.setEnabled(id,enabled); }
}
