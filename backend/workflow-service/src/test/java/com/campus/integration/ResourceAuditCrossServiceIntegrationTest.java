package com.campus.integration;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

/** Real resource JAR, production workflow JVM, HTTP and PostgreSQL; no fake audit receiver. */
class ResourceAuditCrossServiceIntegrationTest {
    @Test void resourceRejectionsReachTheRealWorkflowAuditService() throws Exception {
        try (var h = new RepairCrossServiceIntegrationTest.Processes()) {
            String suffix=UUID.randomUUID().toString().replace("-","").substring(0,10);
            h.bootstrapLogin="raa"+suffix;
            start(h);
            var admin=h.new Client(h.bootstrapLogin);admin.authenticate(false);
            var user=h.new Client("rau"+suffix);user.authenticate(true);
            var me=user.call(h.resource,"GET","/api/auth/me",null);h.status(200,me);
            assertEquals("USER",h.node(me).get("role").asText(),"ordinary registered user is the authorization-denial fixture");

            var attempts=new ArrayList<Attempt>();
            String untrusted="client-trace-"+suffix;
            var anonymous=h.wire.send(HttpRequest.newBuilder(URI.create(h.resource+"/api/resources/rooms"))
                    .timeout(Duration.ofSeconds(12)).header("X-Request-Id",untrusted).GET().build(),HttpResponse.BodyHandlers.ofString());
            attempts.add(rejected(h,"anonymous",anonymous,401,"AUTHENTICATION_REQUIRED",null));
            assertNotEquals(untrusted,attempts.getFirst().trace(),"audit correlation uses the actual server trace");

            var invalidKey=h.wire.send(HttpRequest.newBuilder(URI.create(h.resource+"/internal/resources/rooms"))
                    .timeout(Duration.ofSeconds(12)).header("X-Service-Key","invalid-"+suffix).GET().build(),HttpResponse.BodyHandlers.ofString());
            attempts.add(rejected(h,"invalid service key",invalidKey,401,"SERVICE_AUTHENTICATION_REQUIRED",null));

            // Client.call obtains real CSRF through the actual resource service before this POST.
            var forbidden=user.call(h.resource,"POST","/api/resources/rooms",Map.of());
            attempts.add(rejected(h,"ordinary user with valid CSRF",forbidden,403,"ACCESS_DENIED",user.id));

            // Use the authenticated administrator's real cookie client, deliberately omitting only the CSRF header.
            var csrf=admin.http.send(HttpRequest.newBuilder(URI.create(h.resource+"/api/resources/rooms"))
                    .timeout(Duration.ofSeconds(12)).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());
            attempts.add(rejected(h,"missing CSRF",csrf,403,"CSRF_INVALID",null));
            assertEquals(4,attempts.stream().map(Attempt::trace).distinct().count(),"four actual rejection requests have distinct server traces");

            var delivered=new LinkedHashMap<String,JsonNode>();
            long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();
            do {
                for(var attempt:attempts) {
                    if(delivered.containsKey(attempt.trace()))continue;
                    String query=URLEncoder.encode(attempt.trace(),StandardCharsets.UTF_8);
                    var response=admin.call(h.workflow,"GET","/api/audit?traceId="+query,null);h.status(200,response);
                    for(var event:h.node(response).get("items")) {
                        if("RESOURCE_DENIED".equals(event.get("action").asText())&&"D".equals(event.get("sourceModule").asText())) {
                            assertNull(delivered.put(attempt.trace(),event),"one source rejection produces one matching audit event");
                        }
                    }
                }
                if(delivered.size()==attempts.size())break;
                Thread.sleep(200);
            } while(System.nanoTime()<deadline);

            var missing=attempts.stream().filter(a->!delivered.containsKey(a.trace())).map(Attempt::label).toList();
            assertTrue(missing.isEmpty(),"real resource rejection audit must reach the workflow service for: "+missing);
            for(var attempt:attempts) {
                var event=delivered.get(attempt.trace());
                assertEquals(attempt.trace(),event.get("traceId").asText());
                assertFalse(event.get("success").asBoolean(),"the audit records the real rejected operation");
                assertEquals(attempt.actor(),event.get("actorId").isNull()?null:event.get("actorId").asText(),"only a verified identity can be the audit actor");
                assertEquals(Integer.toString(attempt.status()),event.get("afterData").get("status").asText());
                assertFalse(event.get("afterData").has("roomId"),"unknown roomId must be omitted, rather than sent as null");
                assertTrue(event.get("eventKey").asText().startsWith("M02:"),"the event retains its M02 source event key");
                System.out.println("M02_REAL_REJECTION_AUDIT_PASS PATH="+attempt.label()+" TRACE="+attempt.trace());
            }
        }
    }

    private record Attempt(String label,String trace,int status,String actor) {}
    private Attempt rejected(RepairCrossServiceIntegrationTest.Processes h,String label,HttpResponse<String> response,int status,String code,String actor) {
        h.status(status,response);assertEquals(code,h.node(response).get("code").asText(),"real security rejection reason for "+label);
        String trace=response.headers().firstValue("X-Request-Id").orElse(null);
        assertNotNull(trace,"actual server response trace for "+label);assertDoesNotThrow(()->UUID.fromString(trace));
        System.out.println("M02_REAL_REJECTION PATH="+label+" STATUS="+response.statusCode()+" TRACE="+trace);
        return new Attempt(label,trace,status,actor);
    }

    private void start(RepairCrossServiceIntegrationTest.Processes h) throws Exception {
        var resource=h.launch(List.of(h.java(),"-Xmx256m","-jar",h.resourceJar().toString(),
                "--server.address=127.0.0.1","--server.port="+h.rp,"--spring.profiles.active=local",
                "--campus.auth.secure-cookies=false","--campus.bootstrap-admin.login-name="+h.bootstrapLogin,
                "--campus.reservation.notification-url="+h.workflow,"--campus.resource.audit-url="+h.workflow,
                "--campus.resource.audit-initial-delay-millis=100","--campus.resource.audit-poll-millis=100",
                "--spring.datasource.hikari.maximum-pool-size=2"),true);
        h.health(h.resource,resource);
        String classpath=String.join(File.pathSeparator,Arrays.stream(System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"))
                .split(Pattern.quote(File.pathSeparator))).filter(p->!p.endsWith("test-classes")).toList());
        var workflow=h.launch(List.of(h.java(),"-Xmx256m","-cp",classpath,"com.campus.WorkflowApplication",
                "--server.address=127.0.0.1","--server.port="+h.wp,"--spring.profiles.active=local",
                "--campus.auth.secure-cookies=false","--campus.workflow.identity-url="+h.resource+"/internal/auth/introspect",
                "--campus.repair.workflow-url="+h.workflow,"--campus.approval.delivery-url="+h.workflow,
                "--spring.datasource.hikari.maximum-pool-size=2"),false);
        h.health(h.workflow,workflow);
    }
}
