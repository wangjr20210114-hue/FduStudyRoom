package com.campus.approval;
import com.campus.common.error.ApiException;import com.campus.common.security.AuthSettings;
import java.net.*;import java.net.http.*;import java.time.*;import java.util.*;import org.springframework.core.env.Environment;import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
@Component public class ApprovalGateway {
 static final JsonMapper JSON=JsonMapper.builder().build();final String origin,delivery;final AuthSettings auth;
 final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
 public record Reply(int status,Map<String,Object> body){}
 public ApprovalGateway(AuthSettings auth,Environment env){this.auth=auth;String identity=env.getProperty("campus.workflow.identity-url","http://127.0.0.1:8080/internal/auth/introspect");this.origin=URI.create(identity).resolve("/").toString().replaceAll("/$","");this.delivery=env.getProperty("campus.approval.delivery-url","http://127.0.0.1:8081").replaceAll("/$","");}
 @SuppressWarnings("unchecked") public List<Map<String,Object>> candidates(){
  try{var q=HttpRequest.newBuilder(URI.create(origin+"/internal/identity/users?permission=approval:decide")).header("X-Service-Key",auth.serviceKey()).timeout(Duration.ofSeconds(4)).GET().build();var r=client.send(q,HttpResponse.BodyHandlers.ofString());if(r.statusCode()!=200)throw new ApiException(503,"APPROVAL_DEPENDENCY_UNAVAILABLE");return JSON.readValue(r.body(),List.class);}catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(503,"APPROVAL_DEPENDENCY_UNAVAILABLE");}}
 @SuppressWarnings("unchecked") public Reply exchange(String base,String path,Object data){
  try{var q=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(4)).header("X-Service-Key",auth.serviceKey()).header("Accept","application/json");
   var r=client.send(data==null?q.GET().build():q.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofString());
   Map<String,Object>b=r.body().isBlank()?Map.of():JSON.readValue(r.body(),Map.class);return new Reply(r.statusCode(),b);
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ApiException(503,"APPROVAL_DEPENDENCY_UNAVAILABLE");}catch(Exception e){throw new ApiException(503,"APPROVAL_DEPENDENCY_UNAVAILABLE");}}
 public Map<String,Object> require(String path){var r=exchange(origin,path,null);if(r.status==404)throw new ApiException(404,"APPROVAL_RESOURCE_NOT_FOUND");if(r.status!=200)throw new ApiException(503,"APPROVAL_DEPENDENCY_UNAVAILABLE");return r.body;}
}