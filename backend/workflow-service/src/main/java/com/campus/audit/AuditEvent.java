package com.campus.audit;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public record AuditEvent(@NotBlank @Size(max=255) @Pattern(regexp="[A-Za-z0-9_.:-]+") String eventKey,
 @NotBlank @Pattern(regexp="resource-service|workflow-service") String sourceService,
 UUID actorId,@NotBlank @Size(max=128) @Pattern(regexp="[A-Za-z][A-Za-z0-9_.:-]*") String action,
 @NotBlank @Pattern(regexp="M1|M2|M3|D|EXTERNAL") String sourceModule,UUID sourceId,
 @NotNull Boolean success,@NotNull Instant occurredAt,
 @NotBlank @Size(max=64) @Pattern(regexp="[A-Za-z0-9_.:-]+") String traceId,
 Map<String,Object> beforeData,Map<String,Object> afterData) {
 @Override public String toString(){return "AuditEvent[redacted]";}
}
