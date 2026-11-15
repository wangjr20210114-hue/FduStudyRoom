package com.campus.statistics;
import com.campus.common.error.ApiException;
import com.campus.common.security.AuthSettings;
import java.math.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Consumes aggregate HTTP contracts only; has no database dependency. */
@Service
public class StatisticsService {
 private final URI resources,reservations;private final AuthSettings auth;private final JsonMapper json;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 public StatisticsService(AuthSettings auth,JsonMapper json,
  @Value("${campus.workflow.identity-url:http://127.0.0.1:8080/internal/auth/introspect}") String identity,
  @Value("${campus.statistics.resource-url:}") String resource,@Value("${campus.statistics.reservation-url:}") String reservation){
  this.auth=auth;this.json=json;
  // Reuse the existing identity-service origin: Compose uses resource, local uses loopback.
  // The identity fallback is deliberately identical to RemoteIdentityLookup's public setting.
  URI origin=endpoint(identity);
  this.resources=endpoint(resource.isBlank()?origin.resolve("/internal/resources/statistics").toString():resource);
  this.reservations=endpoint(reservation.isBlank()?origin.resolve("/internal/reservations/statistics").toString():reservation);
 }
 private URI endpoint(String value){URI uri=URI.create(value);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Invalid statistics source endpoint");return uri;}
 private ApiException unavailable(){return new ApiException(503,"STATISTICS_SOURCE_UNAVAILABLE");}
 private String encode(Object v){return URLEncoder.encode(v.toString(),StandardCharsets.UTF_8);}
 private JsonNode source(URI url,Instant from,Instant to,Instant asOf,UUID room){
  String query="?from="+encode(from)+"&to="+encode(to)+"&asOf="+encode(asOf)+(room==null?"":"&roomId="+room);
  try{
   var response=http.send(HttpRequest.newBuilder(URI.create(url+query)).timeout(Duration.ofSeconds(3)).header("X-Service-Key",auth.serviceKey()).header("Accept","application/json").GET().build(),HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()!=200||response.body().length()>200000)throw unavailable();var n=json.readTree(response.body());
   if(!n.isObject()||!Instant.parse(n.path("from").asText()).equals(from)||!Instant.parse(n.path("to").asText()).equals(to)||!Instant.parse(n.path("asOf").asText()).equals(asOf)||!Objects.equals(room==null?null:room.toString(),n.path("roomId").isNull()?null:n.path("roomId").asText())||n.path("algorithm").asText().isBlank())throw unavailable();
   return n;
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}catch(Exception e){throw unavailable();}
 }
 private BigDecimal number(JsonNode source,String field){var v=source.path(field);if(!v.isNumber())throw unavailable();BigDecimal number=v.decimalValue();if(number.signum()<0||number.compareTo(new BigDecimal("9223372036854775807"))>0)throw unavailable();return number;}
 private long count(JsonNode source,String field){var n=number(source,field);try{return n.longValueExact();}catch(ArithmeticException invalid){throw unavailable();}}
 private BigDecimal ratio(BigDecimal numerator,BigDecimal denominator){return denominator.signum()==0?null:numerator.divide(denominator,MathContext.DECIMAL128);}
 public Map<String,Object> calculate(Instant from,Instant to,Instant asOf,UUID room){
  if(from==null||to==null||asOf==null||!from.isBefore(to))throw new ApiException(400,"INVALID_STATISTICS_RANGE");
  var resource=source(resources,from,to,asOf,room);var reservation=source(reservations,from,to,asOf,room);var result=new LinkedHashMap<String,Object>();
  result.put("from",from);result.put("to",to);result.put("asOf",asOf);result.put("roomId",room);result.put("algorithm","P08-retained-seat-seconds-and-deadline-cohort-v1");
  result.put("sources",List.of(Map.of("service","resource","algorithm",resource.path("algorithm").asText()),Map.of("service","reservation","algorithm",reservation.path("algorithm").asText())));
  if(!resource.path("historyComplete").isBoolean())throw unavailable();boolean complete=resource.path("historyComplete").asBoolean();
  String available=resource.path("historyAvailableFrom").isNull()?null:resource.path("historyAvailableFrom").asText();if(available!=null){try{Instant.parse(available);}catch(Exception e){throw unavailable();}}
  result.put("historyComplete",complete);result.put("historyAvailableFrom",available);
  long checked=count(reservation,"checkedInCount"),noShow=count(reservation,"noShowCount"),cohort;
  try{cohort=Math.addExact(checked,noShow);}catch(ArithmeticException e){throw unavailable();}
  result.put("checkedInCount",checked);result.put("noShowCount",noShow);result.put("cohortCount",cohort);
  for(String field:List.of("excludedBeforeStartCancelledCount","excludedSystemTerminatedCount","notDueCount"))result.put(field,count(reservation,field));
  result.put("checkInRate",ratio(BigDecimal.valueOf(checked),BigDecimal.valueOf(cohort)));result.put("noShowRate",ratio(BigDecimal.valueOf(noShow),BigDecimal.valueOf(cohort)));
  if(!complete){
   result.put("status","HISTORY_UNAVAILABLE");result.put("checkInRate",null);result.put("noShowRate",null);for(String key:List.of("capacitySeatSeconds","occupiedSeatSeconds","occupiedM1SeatSeconds","occupiedM2SeatSeconds","confirmedBookingCount","confirmedM1BookingCount","confirmedM2BookingCount","occupancyRate"))result.put(key,null);
   return result;
  }
  BigDecimal capacity=number(resource,"capacitySeatSeconds"),occupied=number(resource,"occupiedSeatSeconds"),m1=number(resource,"occupiedM1SeatSeconds"),m2=number(resource,"occupiedM2SeatSeconds");
  long bookings=count(resource,"confirmedBookingCount"),b1=count(resource,"confirmedM1BookingCount"),b2=count(resource,"confirmedM2BookingCount");
  if(m1.add(m2).compareTo(occupied)!=0||occupied.compareTo(capacity)>0||b1>bookings||b2!=bookings-b1)throw unavailable();
  result.put("capacitySeatSeconds",capacity);result.put("occupiedSeatSeconds",occupied);result.put("occupiedM1SeatSeconds",m1);result.put("occupiedM2SeatSeconds",m2);result.put("confirmedBookingCount",bookings);result.put("confirmedM1BookingCount",b1);result.put("confirmedM2BookingCount",b2);result.put("occupancyRate",ratio(occupied,capacity));result.put("status",capacity.signum()==0?"NO_CAPACITY":"OK");return result;
 }
}
