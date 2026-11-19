package com.campus.buffer;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/team/github")
public class GitHubEnrollment {
    private final JdbcTemplate database;

    GitHubEnrollment(JdbcTemplate database) {
        this.database = database;
    }

    public record Account(long id, String login) {}
    private static final Set<String> SLOTS = Set.of("a", "b", "c", "d", "e", "f");

    @PostMapping
    public Map<String, String> register(HttpServletRequest request, @RequestBody Account account) {
        var member = (BufferSettings.Member) request.getAttribute("member");
        if (member.admin() || !SLOTS.contains(member.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (account.id() <= 0 || account.login() == null ||
                !account.login().matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        int bound = database.update("""
            INSERT INTO buffer.github_enrollments AS current (member_id, github_id, github_login)
            VALUES (?, ?, ?) ON CONFLICT DO NOTHING
            """, member.id(), account.id(), account.login());
        if (bound == 0) {
            int sameAccount = database.update("""
                UPDATE buffer.github_enrollments SET github_login = ?
                WHERE member_id = ? AND github_id = ?
                """, account.login(), member.id(), account.id());
            if (sameAccount != 1) throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return Map.of("member", member.id(), "github", account.login());
    }

    @GetMapping
    public List<Map<String, Object>> registrations(HttpServletRequest request) {
        var member = (BufferSettings.Member) request.getAttribute("member");
        if (!member.admin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return database.queryForList("""
            SELECT member_id, github_id, github_login FROM buffer.github_enrollments ORDER BY member_id
            """);
    }
}
