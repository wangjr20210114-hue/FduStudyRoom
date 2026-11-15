package com.campus.reservation;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Coordinator QA: real application HTTP/PostgreSQL; typed F-contract HTTP fixture, not the real F service. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationFailureAuditBaselineTest {
    private ReservationApiTest h;
    private final List<AuditWire> received = new CopyOnWriteArrayList<>();
    private final AtomicReference<Throwable> fixtureFailure = new AtomicReference<>();
    private static final String FORGED_TRACE = "untrusted-client-trace";
    private static final UUID FORGED_ACTOR = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");

    record AuditWire(String eventKey, String sourceService, UUID actorId, String action,
                     String sourceModule, UUID sourceId, Boolean success, Instant occurredAt,
                     String traceId, Map<String,Object> beforeData, Map<String,Object> afterData) {}

    @BeforeAll void start() throws Exception {
        h = new ReservationApiTest();
        h.start();
        h.notifications.createContext("/internal/audit/events", this::receiveAudit);
    }
    @BeforeEach void arrange() throws Exception {
        h.arrange();
        received.clear();
        fixtureFailure.set(null);
    }
    @AfterAll void stop() throws Exception { if (h != null) h.stop(); }

    private void receiveAudit(HttpExchange exchange) throws IOException {
        try {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals(ReservationApiTest.KEY, exchange.getRequestHeaders().getFirst("X-Service-Key"));
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            var event = ReservationApiTest.JSON.readValue(bytes, AuditWire.class);
            assertTrue(event.eventKey().matches("[A-Za-z0-9_.:-]{1,255}"));
            assertEquals("resource-service", event.sourceService());
            assertEquals("RESERVATION_ACCESS_DENIED", event.action());
            assertEquals("M1", event.sourceModule());
            assertNull(event.sourceId());
            assertEquals(Boolean.FALSE, event.success());
            assertNotNull(event.occurredAt());
            assertTrue(event.traceId().matches("[A-Za-z0-9_.:-]{1,64}"));
            assertEquals(Map.of(), event.beforeData());
            assertEquals(Set.of("status"), event.afterData().keySet());
            assertTrue(event.afterData().get("status") instanceof String);
            assertTrue(Set.of("401", "403").contains(event.afterData().get("status")));
            received.add(event);
            byte[] response = ReservationApiTest.JSON.writeValueAsBytes(Map.of("eventKey",event.eventKey(),"duplicate",false));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
        } catch (Throwable failure) {
            fixtureFailure.compareAndSet(null, failure);
            exchange.sendResponseHeaders(400, -1);
        } finally { exchange.close(); }
    }

    private HttpResponse<String> request(HttpClient client, String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(h.base + path))
            .header("X-Request-Id", FORGED_TRACE).header("X-Actor-Id", FORGED_ACTOR.toString())
            .header("X-User-Id", FORGED_ACTOR.toString()).header("Content-Type", "application/json")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }

    private String trace(HttpResponse<String> response) {
        String value = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertNotEquals(FORGED_TRACE, value, "audit must use the server request trace");
        return value;
    }

    private long count(String trace) {
        return h.db.queryForObject("SELECT count(*) FROM m1.reservation_outbox WHERE kind='AUDIT' AND payload->>'action'='RESERVATION_ACCESS_DENIED' AND payload->>'traceId'=?", Long.class, trace);
    }

    private AuditWire durable(HttpResponse<String> response, int status, UUID actor) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        String trace = trace(response);
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (count(trace) == 0 && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(1, count(trace), "security denial must persist one durable audit with its server trace");
        var row = h.db.queryForMap("SELECT reservation_id,payload::text AS payload,status FROM m1.reservation_outbox WHERE kind='AUDIT' AND payload->>'traceId'=?", trace);
        assertNull(row.get("reservation_id"), "pre-business denial has no reservation foreign key");
        assertEquals("PENDING", row.get("status"), "denial must be durable before delivery");
        String raw = row.get("payload").toString();
        var event = ReservationApiTest.JSON.readValue(raw, AuditWire.class);
        assertEquals(actor, event.actorId(), "actor must come from verified server principal or remain unknown");
        assertNotEquals(FORGED_ACTOR, event.actorId());
        assertEquals(trace, event.traceId());
        assertEquals(Map.of("status", String.valueOf(status)), event.afterData());
        assertFalse(raw.contains(FORGED_TRACE));
        assertFalse(raw.contains("password"));
        assertFalse(raw.contains("student@example.test"));
        h.tick();
        assertNull(fixtureFailure.get(), "typed audit contract fixture must accept the actual payload");
        assertEquals(List.of(event), received.stream().filter(e -> e.traceId().equals(trace)).toList());
        assertEquals("DONE", h.db.queryForObject("SELECT status FROM m1.reservation_outbox WHERE payload->>'traceId'=?", String.class, trace));
        h.tick();
        assertEquals(1, received.stream().filter(e -> e.traceId().equals(trace)).count(), "successful durable audit is not delivered twice");
        return event;
    }

    @Test void anonymous401HasDurableAuditAndServerTraceWithoutGuessedActor() throws Exception {
        var response = request(h.internal, "GET", "/api/reservations", null);
        durable(response, 401, null);
    }

    @Test void missingCsrf403HasDurableAuditWithoutGuessingActorFromHeaders() throws Exception {
        var response = request(h.user, "POST", "/api/reservations", "{}");
        durable(response, 403, null);
    }

    @Test void authenticated403RetainsVerifiedActorRatherThanForgedHeader() throws Exception {
        var response = request(h.user, "GET", "/api/admin/reservations", null);
        durable(response, 403, h.userId);
    }

    @Test void ordinary400AndSuccessful200AreNotPermissionDenials() throws Exception {
        var malformed = request(h.user, "GET", "/api/reservations/availability", null);
        assertEquals(400, malformed.statusCode(), malformed.body());
        assertEquals(0, count(trace(malformed)), "ordinary input rejection is not an access-denied audit");
        var allowed = request(h.user, "GET", "/api/reservations", null);
        assertEquals(200, allowed.statusCode(), allowed.body());
        assertEquals(0, count(trace(allowed)));
        assertTrue(received.isEmpty());
    }
}
