package com.campus.workflow;
import com.campus.common.error.ApiException;
import com.campus.common.security.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RemoteIdentityLookupTest {
 private AuthSettings settings(){return new AuthSettings("password",Base64.getEncoder().encodeToString(new byte[32]),"s".repeat(40),false,Duration.ofMinutes(30));}
 @Test void callsRealHttpAndReadsCurrentPermissions()throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var id=UUID.randomUUID();var settings=settings();var tokens=new TokenService(settings);
  server.createContext("/introspect",exchange->{
   assertEquals(settings.serviceKey(),exchange.getRequestHeaders().getFirst("X-Service-Key"));
   assertEquals(id,tokens.verify(exchange.getRequestHeaders().getFirst("Authorization").substring(7)).userId());
   byte[] body=("{\"id\":\""+id+"\",\"loginName\":\"user\",\"displayName\":\"User\",\"role\":\"USER\",\"permissions\":[\"identity:read\"],\"authVersion\":2,\"profileIncomplete\":false}").getBytes(StandardCharsets.UTF_8);
   exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
  });server.start();
  try {var client=new RemoteIdentityLookup(tokens,settings,"http://127.0.0.1:"+server.getAddress().getPort()+"/introspect");var identity=client.resolve(new TokenService.Claims(id,2));assertEquals(Set.of("identity:read"),identity.permissions());assertEquals(id,identity.id());}
  finally{server.stop(0);}
 }
 @Test void deniesRevokedSessionAndUnavailableService()throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/introspect",exchange->{exchange.sendResponseHeaders(401,-1);exchange.close();});server.start();
  var settings=settings();var client=new RemoteIdentityLookup(new TokenService(settings),settings,"http://127.0.0.1:"+server.getAddress().getPort()+"/introspect");
  try{assertEquals(401,assertThrows(ApiException.class,()->client.resolve(new TokenService.Claims(UUID.randomUUID(),1))).status());}finally{server.stop(0);}
  assertEquals(503,assertThrows(ApiException.class,()->client.resolve(new TokenService.Claims(UUID.randomUUID(),1))).status());
 }
}
