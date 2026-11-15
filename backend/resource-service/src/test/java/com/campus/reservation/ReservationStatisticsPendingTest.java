package com.campus.reservation;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Additional actual endpoint observation; GET must not execute the scheduler. */
class ReservationStatisticsPendingTest {
 @Test void dueConfirmedWithoutOutcomeIsExplicitlyPending()throws Exception{
  var h=new ReservationApiTest();h.start();try{h.arrange();String checked=h.reserve(h.user,h.seat,"09:00","10:00");String awaiting=h.reserve(h.other,h.seat2,"09:00","10:00");var third=h.client();h.register(third);h.loseAllocateAck=true;var unknown=h.post(third,h.booking(h.otherSeat,"09:00","10:00"));h.status(202,unknown);h.loseAllocateAck=false;
   h.at("09:00:00");h.status(200,h.check(h.user,checked,h.code(h.room)));h.at("09:15:00");String query="/internal/reservations/statistics?from="+h.enc(h.time("09:00"))+"&to="+h.enc(h.time("11:00"))+"&asOf="+h.enc(h.time("09:15"));var r=h.in("GET",query,null);h.status(200,r);var n=h.tree(r);assertEquals(1,n.path("checkedInCount").asInt());assertEquals(0,n.path("noShowCount").asInt());assertEquals(1,n.path("pendingEvaluationCount").asInt(),"only previously confirmed due reservations without a persisted outcome are pending evaluation");assertEquals(1,n.path("excludedUnconfirmedCount").asInt());assertEquals(0,n.path("notDueCount").asInt());assertEquals("RESERVED",h.get(h.other,awaiting).path("status").asText());assertEquals(0,h.violations(awaiting));
  }finally{h.stop();}
 }
}
