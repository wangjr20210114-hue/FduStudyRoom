package com.campus.reservation;
import com.campus.common.error.ApiException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** No caller holds an M03 JDBC transaction while this adapter performs HTTP. */
final class ReservationGateway implements AutoCloseable {
 final Supplier<URI> self;final URI resources,notifications;final String key;
 final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 final JsonMapper json=JsonMapper.builder().build();
 record Reply(int status,JsonNode body){}
 ReservationGateway(Supplier<URI> self,String resource,String notifications,String key){this.self=self;this.resources=resource==null||resource.isBlank()?null:valid(resource);this.notifications=valid(notifications);this.key=key;}
 static URI valid(String raw){URI u=URI.create(raw);if(!Set.of("http","https").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)throw new IllegalArgumentException("Invalid reservation service endpoint");return u;}
 URI resource(String path){return (resources==null?self.get():resources).resolve(path);}
 Reply request(URI uri,String method,Object payload){try{var b=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).header("X-Service-Key",key).header("Content-Type","application/json");var r=http.send(b.method(method,payload==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),HttpResponse.BodyHandlers.ofString());return new Reply(r.statusCode(),r.body().isBlank()?json.createObjectNode():json.readTree(r.body()));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ApiException(503,"RESERVATION_DEPENDENCY_UNAVAILABLE");}catch(Exception e){throw new ApiException(503,"RESERVATION_DEPENDENCY_UNAVAILABLE");}}
 JsonNode required(URI uri){var r=request(uri,"GET",null);if(r.status()!=200)throw new ApiException(r.status()>=500?503:r.status(),"RESERVATION_DEPENDENCY_"+r.status());return r.body();}
 Map<String,Object> object(JsonNode n){return json.convertValue(n,Map.class);}
 Map<String,Object> settings(){return object(required(self.get().resolve("/internal/system/settings")));}
 Map<String,Object> seat(UUID id){return object(required(resource("/internal/resources/seats/"+id)));}
 Map<String,Object> room(UUID id){return object(required(resource("/internal/resources/rooms/"+id)));}
 Map<String,Object> eligibility(UUID id){return object(required(self.get().resolve("/internal/identity/users/"+id+"/eligibility")));}
 String email(UUID id){var n=required(self.get().resolve("/internal/identity/users/"+id+"/notification-email"));return n.path("enabled").asBoolean()&&n.path("verified").asBoolean()&&!n.path("email").isNull()?n.path("email").asText():null;}
 static String enc(Object s){return URLEncoder.encode(s.toString(),StandardCharsets.UTF_8);}
 public void close(){http.close();}
}
