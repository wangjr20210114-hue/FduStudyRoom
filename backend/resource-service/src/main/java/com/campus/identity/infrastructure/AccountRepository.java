package com.campus.identity.infrastructure;

import com.campus.common.error.ApiException;
import com.campus.identity.domain.Account;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private static final String SELECT = """
        SELECT u.id,u.login_name,u.display_name,u.email,u.role_id,r.code AS role_code,
               r.enabled AS role_enabled,u.enabled,u.auth_version,u.department_code,u.can_apply_room
        FROM iam.users u JOIN iam.roles r ON r.id=u.role_id
        """;
    private final JdbcTemplate jdbc;
    public AccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Account> find(UUID id) {
        return jdbc.query(SELECT + " WHERE u.id=?", (rs, row) -> {
            UUID roleId = rs.getObject("role_id", UUID.class);
            Set<String> permissions = new HashSet<>(jdbc.queryForList("""
                SELECT p.code FROM iam.role_permissions rp JOIN iam.permissions p ON p.id=rp.permission_id WHERE rp.role_id=?
                """, String.class, roleId));
            return new Account(rs.getObject("id", UUID.class), rs.getString("login_name"), rs.getString("display_name"),
                    rs.getString("email"), roleId, rs.getString("role_code"), rs.getBoolean("role_enabled"),
                    rs.getBoolean("enabled"), rs.getInt("auth_version"), permissions);
        }, id).stream().findFirst();
    }

    public record Credentials(UUID id, String passwordHash) {
        @Override public String toString() { return "Credentials[id=" + id + ", passwordHash=REDACTED]"; }
    }
    public Optional<Credentials> credentials(String loginName) {
        return jdbc.query("SELECT id,password_hash FROM iam.users WHERE identity_source='LOCAL' AND lower(login_name)=?",
                (rs, row) -> new Credentials(rs.getObject("id", UUID.class), rs.getString("password_hash")),
                loginName).stream().findFirst();
    }

    public Account create(String loginName, String passwordHash, String roleCode) {
        try {
            var ids = jdbc.query("""
                INSERT INTO iam.users(login_name,display_name,password_hash,role_id)
                SELECT ?,?,?,id FROM iam.roles WHERE code=? AND enabled
                RETURNING id
                """, (rs, row) -> rs.getObject("id", UUID.class), loginName, loginName, passwordHash, roleCode);
            if (ids.isEmpty()) throw new ApiException(503, "ROLE_UNAVAILABLE");
            return find(ids.getFirst()).orElseThrow(() -> new ApiException(503, "IDENTITY_UNAVAILABLE"));
        } catch (DuplicateKeyException error) { throw new ApiException(409, "LOGIN_NAME_TAKEN"); }
    }

    public record Role(UUID id, String code, String name, boolean enabled,Set<String> permissions) {}
    public List<Role> roles() {
        return jdbc.query("SELECT id,code,name,enabled FROM iam.roles ORDER BY code",
                (rs, row) -> new Role(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getBoolean("enabled"),Set.copyOf(jdbc.queryForList("SELECT p.code FROM iam.role_permissions rp JOIN iam.permissions p ON p.id=rp.permission_id WHERE rp.role_id=?",String.class,rs.getObject("id",UUID.class)))));
    }

    public record UserRow(UUID id, String loginName, String displayName, UUID roleId, String role, boolean enabled, int authVersion,String departmentCode,boolean canApplyRoom) {}
    public List<UserRow> listUsers() {
        return jdbc.query(SELECT + " ORDER BY u.login_name LIMIT 100", (rs, row) ->
                new UserRow(rs.getObject("id", UUID.class), rs.getString("login_name"), rs.getString("display_name"),
                        rs.getObject("role_id", UUID.class), rs.getString("role_code"), rs.getBoolean("enabled"), rs.getInt("auth_version"),rs.getString("department_code"),rs.getBoolean("can_apply_room")));
    }

    public boolean updateAccess(UUID id, UUID roleId, boolean enabled, int expectedVersion) {
        return jdbc.update("""
            UPDATE iam.users SET role_id=?,enabled=?,auth_version=auth_version+1,updated_at=now()
            WHERE id=? AND auth_version=? AND (NOT ? OR password_hash IS NOT NULL)
            """, roleId, enabled, id, expectedVersion, enabled) == 1;
    }

    public void revoke(UUID id) {
        jdbc.update("UPDATE iam.users SET auth_version=auth_version+1,updated_at=now() WHERE id=?", id);
    }
}
