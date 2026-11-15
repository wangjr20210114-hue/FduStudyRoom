package com.campus.reservation;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReservationSecurityAuditTest {
 @Test void authenticatedDenialKeepsActorAndRequestTrace()throws Exception {
  var h=new ReservationApiTest();h.start();try{h.arrange();String id=h.reserve(h.user,h.seat,"09:00","10:00");var denial=h.cancel(h.other,id,UUID.randomUUID());h.status(403,denial);var row=h.db.queryForMap("SELECT payload::text AS payload FROM m1.reservation_outbox WHERE kind='AUDIT' AND payload->>'action'='RESERVATION_ACCESS_DENIED' ORDER BY created_at DESC LIMIT 1");var payload=ReservationApiTest.JSON.readTree(row.get("payload").toString());assertEquals(h.otherId.toString(),payload.path("actorId").asText(),"authenticated denial must retain the actor and request trace");assertEquals(denial.headers().firstValue("X-Request-Id").orElseThrow(),payload.path("traceId").asText(),"authenticated denial must retain the actor and request trace");assertFalse(row.get("payload").toString().contains("student@example.test"));
  }finally{h.stop();}
 }
}
