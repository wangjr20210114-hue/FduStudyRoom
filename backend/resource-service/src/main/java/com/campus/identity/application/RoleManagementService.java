package com.campus.identity.application;

import com.campus.identity.infrastructure.RoleRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleManagementService {
    private final RoleRepository roles;
    private final com.campus.identity.infrastructure.IdentityAuditOutbox audit;
    public RoleManagementService(RoleRepository roles,com.campus.identity.infrastructure.IdentityAuditOutbox audit) { this.roles=roles;this.audit=audit; }
    @Transactional public RoleRepository.RoleDetails create(String code,String name,Set<String> permissions) { var result=roles.create(code,name.trim(),permissions);audit.enqueue("ROLE_CREATED",result.id(),java.util.Map.of(),java.util.Map.of("roleCode",code,"permissions",permissions));return result; }
    @Transactional public void replacePermissions(UUID id,Set<String> permissions) { var before=roles.lockedDetails(id);roles.replacePermissions(id,permissions);audit.enqueue("ROLE_PERMISSIONS_CHANGED",id,java.util.Map.of("permissions",before.permissions()),java.util.Map.of("permissions",permissions)); }
    @Transactional public void setEnabled(UUID id,boolean enabled) { var before=roles.lockedDetails(id);roles.setEnabled(id,enabled);audit.enqueue("ROLE_ENABLED_CHANGED",id,java.util.Map.of("enabled",before.enabled()),java.util.Map.of("enabled",enabled)); }
}
