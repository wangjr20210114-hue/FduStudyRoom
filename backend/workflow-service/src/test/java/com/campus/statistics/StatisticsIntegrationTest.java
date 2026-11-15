package com.campus.statistics;
import com.campus.notification.SupportHttpFixture;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatisticsIntegrationTest extends SupportHttpFixture {
 String query(){return "/api/statistics?from="+from+"&to="+to+"&asOf="+asOf+"&roomId="+room;}
 @Test void calculatesExactOccupancyAndSharedCohort()throws Exception {
  var r=get(query());assertEquals("OK",r.path("status").asText());assertEquals(28800,r.path("capacitySeatSeconds").asLong());assertEquals(10800,r.path("occupiedSeatSeconds").asLong());assertEquals(.375,r.path("occupancyRate").asDouble(),1e-12);assertEquals(.5,r.path("checkInRate").asDouble(),1e-12);assertEquals(.5,r.path("noShowRate").asDouble(),1e-12);assertEquals(2,r.path("cohortCount").asLong());assertEquals(2,r.path("confirmedBookingCount").asLong());assertEquals(1,r.path("excludedBeforeStartCancelledCount").asLong());assertEquals(2,sourceRequests.size());
  for(String request:sourceRequests){String decoded=java.net.URLDecoder.decode(request,StandardCharsets.UTF_8);assertTrue(decoded.contains("asOf="+asOf));assertTrue(decoded.contains("roomId="+room));}assertTrue(r.path("sources").size()>=2);assertFalse(r.path("algorithm").asText().isBlank());
 }
 @Test void distinguishesZeroCapacityFromMissingHistory()throws Exception {
  zero=true;var r=get(query());assertEquals("NO_CAPACITY",r.path("status").asText());assertTrue(r.path("occupancyRate").isNull());assertTrue(r.path("checkInRate").isNull());assertEquals(0,r.path("cohortCount").asInt());
  zero=false;history=false;r=get(query());assertEquals("HISTORY_UNAVAILABLE",r.path("status").asText());assertTrue(r.path("capacitySeatSeconds").isNull());assertTrue(r.path("occupancyRate").isNull());assertFalse(r.path("historyAvailableFrom").asText().isBlank());assertFalse(r.path("historyComplete").asBoolean());
 }
 @Test void rejectsFailedMismatchedAndInvalidSources()throws Exception {
  get(query());sourceStatus=503;var r=require(request("GET",query(),null,cookie(user),null),503);assertEquals("STATISTICS_SOURCE_UNAVAILABLE",r.path("code").asText());sourceStatus=200;wrongScope=true;require(request("GET",query(),null,cookie(user),null),503);wrongScope=false;brokenSource=true;require(request("GET",query(),null,cookie(user),null),503);
 }
 @Test void requiresCurrentPermissionAndValidRange()throws Exception {
  get(query());require(request("GET",query(),null,null,null),401);require(request("GET",query(),null,cookie(denied),null),403);permit=false;require(request("GET",query(),null,cookie(user),null),403);assertEquals(2,sourceRequests.size());permit=true;
  require(request("GET","/api/statistics?from="+to+"&to="+from+"&asOf="+asOf,null,cookie(user),null),400);require(request("GET","/api/statistics?from=bad&to="+to+"&asOf="+asOf,null,cookie(user),null),400);
 }
}
