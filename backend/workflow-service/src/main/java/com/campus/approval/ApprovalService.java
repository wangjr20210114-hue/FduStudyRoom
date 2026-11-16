package com.campus.approval;

import com.campus.common.error.ApiException;
import com.campus.common.security.IdentityPrincipal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Executes an existing, submission-time workflow snapshot; never allocates resources. */
@Service
public class ApprovalService {
    public record Decision(Integer expectedVersion, String decision, String comment) {}
    public record TaskView(UUID id, int stepNo, UUID approverId, String status,
            OffsetDateTime dueAt, OffsetDateTime actedAt, UUID decidedBy, String comment) {}
    public record ApprovalView(UUID requestId, String status, int version, String flowVersion,
            OffsetDateTime expiresAt, List<TaskView> tasks) {}
    private record Request(UUID id, UUID applicant, String status, int version,
            String flowVersion, OffsetDateTime expiresAt) {}
    private final JdbcTemplate db;
    private final ApprovalEngine engine;

    public ApprovalService(JdbcTemplate db, ApprovalEngine engine) { this.db = db; this.engine=engine; }

    @Transactional
    public Object read(UUID requestId, IdentityPrincipal actor) {
        permission(actor, "approval:read");
        Request request = lock(requestId);
        List<TaskView> tasks = tasks(requestId);
        if (!request.applicant.equals(actor.id()) && tasks.stream().noneMatch(t -> t.approverId.equals(actor.id()))) {
            throw new ApiException(403, "APPROVAL_READ_FORBIDDEN");
        }
        return engine.details(requestId);
    }

    @Transactional
    public ApprovalView decide(UUID requestId, UUID taskId, Decision input, IdentityPrincipal actor) {
        permission(actor, "approval:decide");
        if (input == null || input.expectedVersion == null || input.expectedVersion < 1
                || !Set.of("APPROVE", "REJECT").contains(input.decision == null ? "" : input.decision)
                || (input.comment != null && input.comment.length() > 2000)
                || ("REJECT".equals(input.decision) && (input.comment == null || input.comment.isBlank()))) {
            throw new ApiException(400, "INVALID_APPROVAL_DECISION");
        }
        // All decision writers lock the request first. Under READ COMMITTED, the
        // second concurrent writer reads the committed version after waiting.
        Request request = lock(requestId);
        if (request.version != input.expectedVersion) throw new ApiException(409, "APPROVAL_VERSION_CONFLICT");
        if (!"PENDING".equals(request.status) || !request.expiresAt.isAfter(OffsetDateTime.ofInstant(engine.now(), java.time.ZoneOffset.UTC))) {
            throw new ApiException(409, "APPROVAL_NOT_PENDING");
        }
        List<TaskView> tasks = tasks(requestId);
        TaskView task = tasks.stream().filter(t -> t.id.equals(taskId)).findFirst()
                .orElseThrow(() -> new ApiException(404, "APPROVAL_TASK_NOT_FOUND"));
        if (!task.approverId.equals(actor.id())) throw new ApiException(403, "APPROVAL_ACTOR_FORBIDDEN");
        if (request.applicant.equals(actor.id())) throw new ApiException(403, "SELF_APPROVAL_FORBIDDEN");
        validatePlan(request, tasks);
        if (tasks.stream().anyMatch(t -> !t.id.equals(taskId) && "APPROVED".equals(t.status)
                && actor.id().equals(t.decidedBy))) {
            throw new ApiException(403, "REPEATED_APPROVER_FORBIDDEN");
        }
        if (!"PENDING".equals(task.status) || task.dueAt == null || !task.dueAt.isAfter(OffsetDateTime.ofInstant(engine.now(), java.time.ZoneOffset.UTC))
                || tasks.stream().anyMatch(t -> t.stepNo < task.stepNo && !"APPROVED".equals(t.status))) {
            throw new ApiException(409, "APPROVAL_STEP_NOT_CURRENT");
        }
        String decidedStatus = "APPROVE".equals(input.decision) ? "APPROVED" : "REJECTED";
        int changed = db.update("""
            UPDATE m2.approval_tasks SET status=?, acted_at=now(), decided_by=?, comment=?, updated_at=now()
            WHERE request_id=? AND id=? AND status='PENDING'
            """, decidedStatus, actor.id(), input.comment == null ? "" : input.comment.strip(), requestId, taskId);
        if (changed != 1) throw new ApiException(409, "APPROVAL_VERSION_CONFLICT");
        String nextStatus;
        if ("REJECTED".equals(decidedStatus)) {
            db.update("""
                UPDATE m2.approval_tasks SET status='CANCELLED',updated_at=now()
                WHERE request_id=? AND status IN ('PENDING','WAITING')
                """, requestId);
            nextStatus = "REJECTED";
        } else if (task.stepNo < tasks.size()) {
            int activated = db.update("""
                UPDATE m2.approval_tasks SET status='PENDING',due_at=?,updated_at=now()
                WHERE request_id=? AND step_no=? AND status='WAITING'
                """, request.expiresAt, requestId, task.stepNo + 1);
            if (activated != 1) throw new ApiException(409, "APPROVAL_STEP_NOT_CURRENT");
            nextStatus = "PENDING";
        } else {
            nextStatus = "RESOURCE_PENDING";
        }
        int updated = db.update("""
            UPDATE m2.classroom_requests SET status=?,version=version+1,updated_at=now()
            WHERE id=? AND version=? AND status='PENDING'
            """, nextStatus, requestId, input.expectedVersion);
        if (updated != 1) throw new ApiException(409, "APPROVAL_VERSION_CONFLICT");
        engine.decided(requestId, actor, nextStatus);
        return view(new Request(request.id, request.applicant, nextStatus, request.version + 1,
                request.flowVersion, request.expiresAt), tasks(requestId));
    }

    private void permission(IdentityPrincipal actor, String permission) { engine.permission(actor, permission); }

    private static void validatePlan(Request request, List<TaskView> tasks) {
        Set<UUID> actors = new HashSet<>();
        if (tasks.size() < 2) throw new ApiException(422, "INVALID_APPROVAL_PLAN");
        for (int i = 0; i < tasks.size(); i++) {
            TaskView task = tasks.get(i);
            if (task.stepNo != i + 1 || !actors.add(task.approverId) || request.applicant.equals(task.approverId)) {
                throw new ApiException(422, "INVALID_APPROVAL_PLAN");
            }
        }
    }

    private Request lock(UUID id) {
        return db.query("""
            SELECT id,applicant_id,status,version,flow_version,expires_at
            FROM m2.classroom_requests WHERE id=? FOR UPDATE
            """, (rs, n) -> new Request(rs.getObject("id", UUID.class), rs.getObject("applicant_id", UUID.class),
                rs.getString("status"), rs.getInt("version"), rs.getString("flow_version"),
                rs.getObject("expires_at", OffsetDateTime.class)), id).stream().findFirst()
                .orElseThrow(() -> new ApiException(404, "APPROVAL_REQUEST_NOT_FOUND"));
    }

    private List<TaskView> tasks(UUID id) {
        return db.query("SELECT * FROM m2.approval_tasks WHERE request_id=? ORDER BY step_no", ApprovalService::task, id);
    }

    private static TaskView task(ResultSet rs, int row) throws SQLException {
        return new TaskView(rs.getObject("id", UUID.class), rs.getInt("step_no"), rs.getObject("approver_id", UUID.class),
                rs.getString("status"), rs.getObject("due_at", OffsetDateTime.class),
                rs.getObject("acted_at", OffsetDateTime.class), rs.getObject("decided_by", UUID.class), rs.getString("comment"));
    }

    private static ApprovalView view(Request request, List<TaskView> tasks) {
        return new ApprovalView(request.id, request.status, request.version, request.flowVersion, request.expiresAt, tasks);
    }
}
