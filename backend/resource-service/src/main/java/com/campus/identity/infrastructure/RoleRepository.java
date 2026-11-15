package com.campus.identity.infrastructure;

import com.campus.common.error.ApiException;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepository {
    private final JdbcTemplate jdbc;
    public RoleRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Permission(String code,String name,String module) {}
    public record RoleDetails(UUID id,String code,String name,boolean enabled,Set<String> permissions) {}
    public List<Permission> permissions() {
        return jdbc.query("SELECT code,name,module FROM iam.permissions ORDER BY module,code",(rs,n)->new Permission(rs.getString("code"),rs.getString("name"),rs.getString("module")));
    }
    public RoleDetails create(String code,String name,Set<String> permissions) {
        validatePermissions(permissions);
        UUID id;
        try { id=jdbc.queryForObject("INSERT INTO iam.roles(code,name) VALUES(?,?) RETURNING id",UUID.class,code,name); }
        catch(DuplicateKeyException e) { throw new ApiException(409,"ROLE_CODE_TAKEN"); }
        insertPermissions(id,permissions);
        return new RoleDetails(id,code,name,true,Set.copyOf(permissions));
    }
    public void replacePermissions(UUID id,Set<String> permissions) {
        lockRole(id);validatePermissions(permissions);
        jdbc.update("DELETE FROM iam.role_permissions WHERE role_id=?",id);
        insertPermissions(id,permissions);
        jdbc.update("UPDATE iam.roles SET updated_at=now() WHERE id=?",id);
    }
    public void setEnabled(UUID id,boolean enabled) {
        lockRole(id);
        jdbc.update("UPDATE iam.roles SET enabled=?,updated_at=now() WHERE id=?",enabled,id);
    }
    public RoleDetails lockedDetails(UUID id) {
        lockRole(id);
        return jdbc.queryForObject("SELECT id,code,name,enabled FROM iam.roles WHERE id=?",(rs,n)->new RoleDetails(id,rs.getString("code"),rs.getString("name"),rs.getBoolean("enabled"),Set.copyOf(jdbc.queryForList("SELECT p.code FROM iam.role_permissions rp JOIN iam.permissions p ON p.id=rp.permission_id WHERE rp.role_id=?",String.class,id))),id);
    }
    private void lockRole(UUID id) {
        if(jdbc.queryForList("SELECT id FROM iam.roles WHERE id=? FOR UPDATE",UUID.class,id).isEmpty()) throw new ApiException(404,"ROLE_NOT_FOUND");
    }
    private void validatePermissions(Set<String> requested) {
        Set<String> known=new HashSet<>(jdbc.queryForList("SELECT code FROM iam.permissions",String.class));
        if(!known.containsAll(requested)) throw new ApiException(400,"UNKNOWN_PERMISSION");
    }
    private void insertPermissions(UUID id,Set<String> permissions) {
        for(String permission:permissions) jdbc.update("INSERT INTO iam.role_permissions(role_id,permission_id) SELECT ?,id FROM iam.permissions WHERE code=?",id,permission);
    }
}
