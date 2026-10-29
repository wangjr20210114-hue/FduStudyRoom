package com.campus.identity.infrastructure;

import com.campus.identity.domain.Account;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private static final String SELECT = """
        SELECT u.id,u.login_name,u.display_name,u.email,u.identity_type,u.identity_source,u.identity_active,
               u.role_id,r.code AS role_code,r.enabled AS role_enabled,u.enabled,u.auth_version
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
                    rs.getString("email"), rs.getString("identity_type"), rs.getString("identity_source"),
                    rs.getBoolean("identity_active"), roleId, rs.getString("role_code"), rs.getBoolean("role_enabled"),
                    rs.getBoolean("enabled"), rs.getInt("auth_version"), permissions);
        }, id).stream().findFirst();
    }

    public record Role(UUID id, String code, String name, boolean enabled) {}
    public List<Role> roles() {
        return jdbc.query("SELECT id,code,name,enabled FROM iam.roles ORDER BY code",
                (rs, row) -> new Role(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getBoolean("enabled")));
    }

    public record UserRow(UUID id, String loginName, String displayName, String identityType, String identitySource,
            UUID roleId, String role, boolean enabled, int authVersion) {}
    public List<UserRow> listUsers() {
        return jdbc.query(SELECT + " ORDER BY u.login_name LIMIT 100", (rs, row) ->
                new UserRow(rs.getObject("id", UUID.class), rs.getString("login_name"), rs.getString("display_name"),
                        rs.getString("identity_type"), rs.getString("identity_source"), rs.getObject("role_id", UUID.class),
                        rs.getString("role_code"), rs.getBoolean("enabled"), rs.getInt("auth_version")));
    }

    public boolean updateAccess(UUID id, UUID roleId, boolean enabled, int expectedVersion) {
        return jdbc.update("""
            UPDATE iam.users SET role_id=?,enabled=?,auth_version=auth_version+1,updated_at=now() WHERE id=? AND auth_version=?
            """, roleId, enabled, id, expectedVersion) == 1;
    }

    public void revoke(UUID id) {
        jdbc.update("UPDATE iam.users SET auth_version=auth_version+1,updated_at=now() WHERE id=?", id);
    }
}
