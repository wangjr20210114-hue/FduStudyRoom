package com.campus.integration;

import java.io.File;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.sql.DriverManager;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Two actual JVMs, real HTTP and PostgreSQL. No provider proxy, fabricated reply or cross-module write. */
class RepairCrossServiceIntegrationTest {
    @Test void maintenanceBroadcastTerminatesPersonalReservationWithoutPenalty() throws Exception {
        try (var h = new Processes()) { h.verify(); }
    }

    static final class Processes implements AutoCloseable {
        final JsonMapper json = JsonMapper.builder().build();
        final String key = UUID.randomUUID()+":"+UUID.randomUUID();
        final String password = UUID.randomUUID()+"Qa1!";
        final String signing;
        final int rp = port(), wp = port();
        final String resource = "http://127.0.0.1:"+rp;
        final String workflow = "http://127.0.0.1:"+wp;
        final String workflowDb = required("CAMPUS_DB_URL");
        final String resourceDb = System.getenv().getOrDefault("CAMPUS_FROZEN_RESOURCE_DB_URL",workflowDb);
        final String resourceUser = System.getenv().getOrDefault("CAMPUS_FROZEN_RESOURCE_DB_USER",required("CAMPUS_DB_USER"));
        final String resourcePassword = System.getenv().getOrDefault("CAMPUS_FROZEN_RESOURCE_DB_PASSWORD",required("CAMPUS_DB_PASSWORD"));
        final HttpClient wire = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        final List<Client> clients = new ArrayList<>();
        final List<Process> processes = new ArrayList<>();
        Processes() {
            byte[] bytes = new byte[32];new SecureRandom().nextBytes(bytes);
            signing = Base64.getEncoder().encodeToString(bytes);
            for(String db:List.of(workflowDb,resourceDb))assertTrue(db.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost|postgres)(:[0-9]+)?/.*"),"integration test requires an isolated local/sandbox database");
        }
        static String required(String name){String v=System.getenv(name);assertNotNull(v,"integration environment missing "+name);return v;}
        static int port(){try(var s=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){return s.getLocalPort();}catch(Exception e){throw new IllegalStateException(e);}}
        static Map<String,Object> map(Object...a){var m=new LinkedHashMap<String,Object>();for(int i=0;i<a.length;i+=2)m.put((String)a[i],a[i+1]);return m;}
        JsonNode node(HttpResponse<String> response){return json.readTree(response.body());}
        void status(int expected,HttpResponse<String> response){assertEquals(expected,response.statusCode(),"real cross-service HTTP response: "+response.body());}
        String java(){return Path.of(System.getProperty("java.home"),"bin","java").toString();}
        Process launch(List<String> command,boolean isResource)throws Exception{
            var b=new ProcessBuilder(command);var e=b.environment();e.put("CAMPUS_AUTH_SIGNING_KEY",signing);e.put("CAMPUS_AUTH_SERVICE_KEY",key);
            e.put("CAMPUS_DB_URL",isResource?resourceDb:workflowDb);
            if(isResource){e.put("CAMPUS_DB_USER",resourceUser);e.put("CAMPUS_DB_PASSWORD",resourcePassword);e.put("CAMPUS_BOOTSTRAP_ADMIN_LOGIN",bootstrapLogin);e.put("CAMPUS_BOOTSTRAP_ADMIN_PASSWORD",password);}
            Path log=Files.createTempFile("repair-cross-service-"+(isResource?"resource":"workflow"),".log");
            Process p=b.redirectErrorStream(true).redirectOutput(log.toFile()).start();processes.add(p);
            System.out.println("M03_M05_QA_PROCESS_LOG="+log);
            return p;
        }
        void health(String origin,Process process)throws Exception{
            long end=System.nanoTime()+Duration.ofSeconds(90).toNanos();
            while(System.nanoTime()<end){assertTrue(process.isAlive(),"real service fixture process exited before health; inspect its private log");try{if(wire.send(HttpRequest.newBuilder(URI.create(origin+"/health")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200)return;}catch(Exception ignored){}Thread.sleep(200);}
            fail("real service fixture health timed out");
        }
        Path resourceJar()throws Exception{
            Path jar=Path.of(System.getenv().getOrDefault("CAMPUS_FROZEN_RESOURCE_JAR","../resource-service/target/resource-service-0.1.0-SNAPSHOT.jar")).toAbsolutePath();
            assertTrue(Files.isRegularFile(jar),"current reactor resource JAR required; do not recursively invoke Maven");
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
            String expected=System.getenv("CAMPUS_FROZEN_RESOURCE_SHA256");if(expected!=null)assertEquals(expected,digest,"frozen resource JAR digest");
            String manifest=System.getenv("CAMPUS_FROZEN_RESOURCE_MANIFEST");if(manifest!=null){var proof=json.readTree(Files.readString(Path.of(manifest)));assertEquals(digest,proof.get("artifacts").get("resource").get("sha256").asText());String commit=System.getenv("CAMPUS_FROZEN_RESOURCE_COMMIT");if(commit!=null)assertEquals(commit,proof.get("coreCommit").asText());}
            System.out.println("M03_M05_REAL_RESOURCE_JAR_SHA256="+digest+" SOURCE="+System.getenv().getOrDefault("CAMPUS_FROZEN_RESOURCE_COMMIT","current-reactor"));return jar;
        }
        void start(String admin)throws Exception{
            var r=launch(List.of(java(),"-Xmx256m","-jar",resourceJar().toString(),"--server.address=127.0.0.1","--server.port="+rp,"--spring.profiles.active=local","--campus.auth.secure-cookies=false","--campus.bootstrap-admin.login-name="+admin,"--campus.reservation.notification-url="+workflow,"--spring.datasource.hikari.maximum-pool-size=2"),true);
            // Keep the generated bootstrap password out of process command lines.
            // The resource JVM reads it only from this test's private environment.
            health(resource,r);
            String cp=String.join(File.pathSeparator,Arrays.stream(System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")).split(java.util.regex.Pattern.quote(File.pathSeparator))).filter(p->!p.endsWith("test-classes")).toList());
            var w=launch(List.of(java(),"-Xmx256m","-cp",cp,"com.campus.WorkflowApplication","--server.address=127.0.0.1","--server.port="+wp,"--spring.profiles.active=local","--campus.auth.secure-cookies=false","--campus.workflow.identity-url="+resource+"/internal/auth/introspect","--campus.repair.workflow-url="+workflow,"--campus.approval.delivery-url="+workflow,"--spring.datasource.hikari.maximum-pool-size=2"),false);health(workflow,w);
        }
        final class Client implements AutoCloseable {
            final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(2)).build();
            final String login;String id;
            Client(String login){this.login=login;clients.add(this);}
            HttpResponse<String> call(String origin,String method,String path,Object body)throws Exception{
                var q=HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(12)).header("Content-Type","application/json");
                if(!method.equals("GET")){var r=http.send(HttpRequest.newBuilder(URI.create(resource+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());status(200,r);var csrf=node(r);q.header(csrf.get("headerName").asText(),csrf.get("token").asText());}
                var response=http.send(q.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
                System.out.println("M03_M05_HTTP "+method+" "+path+" STATUS="+response.statusCode()+" TRACE="+response.headers().firstValue("X-Request-Id").orElse("missing"));return response;
            }
            void authenticate(boolean register)throws Exception{
                HttpResponse<String> response=null;
                for(int i=0;i<4;i++){response=call(resource,"POST",register?"/api/auth/register":"/api/auth/login",map("loginName",login,"password",password));if(register||response.statusCode()!=401)break;Thread.sleep(300);}
                status(register?201:200,response);id=node(response).get("id").asText();
            }
            public void close(){http.close();}
        }
        HttpResponse<String> internal(String path)throws Exception{return wire.send(HttpRequest.newBuilder(URI.create(resource+path)).timeout(Duration.ofSeconds(10)).header("X-Service-Key",key).GET().build(),HttpResponse.BodyHandlers.ofString());}
        JsonNode detail(Client actor,String order)throws Exception{var r=actor.call(workflow,"GET","/api/repairs/"+order,null);status(200,r);return node(r);}
        void verify()throws Exception{
            String suffix=UUID.randomUUID().toString().replace("-","").substring(0,10),adminName="rca"+suffix;
            // launch() sets this only for the newly spawned resource process.
            bootstrapLogin=adminName;start(adminName);
            var admin=new Client(adminName);admin.authenticate(false);var owner=new Client("rco"+suffix);owner.authenticate(true);var tech=new Client("rct"+suffix);tech.authenticate(true);
            var roles=admin.call(resource,"GET","/api/admin/roles",null);status(200,roles);String role=null;for(var x:node(roles))if(x.get("code").asText().equals("MAINTAINER"))role=x.get("id").asText();assertNotNull(role);
            status(204,admin.call(resource,"PATCH","/api/admin/users/"+tech.id+"/access",map("roleId",role,"enabled",true,"expectedAuthVersion",1)));tech.authenticate(false);
            var room=admin.call(resource,"POST","/api/resources/rooms",map("code","RC"+suffix,"name","Real repair broadcast room","campus","QA","building","QA","capacity",2,"openTime","00:00","closeTime","00:00","enabled",true,"bookingEnabled",true));status(201,room);String roomId=node(room).get("id").asText();
            var seat=admin.call(resource,"POST","/api/resources/rooms/"+roomId+"/seats",map("code","A01","socketType","FIXED","enabled",true));status(201,seat);String seatId=node(seat).get("id").asText();
            Instant from=Instant.now().plusSeconds(7200).truncatedTo(ChronoUnit.HOURS),to=from.plusSeconds(3600);
            var booking=owner.call(resource,"POST","/api/reservations",map("operationId",UUID.randomUUID(),"seatId",seatId,"startsAt",from.toString(),"endsAt",to.toString()));status(201,booking);String bookingId=node(booking).get("id").asText();assertEquals("RESERVED",node(booking).get("status").asText());
            var report=owner.call(workflow,"POST","/api/repairs",map("operationId",UUID.randomUUID(),"roomId",roomId,"description","Real cross-service broadcast regression"));status(201,report);String order=node(report).get("id").asText();
            var assigned=admin.call(workflow,"POST","/api/repairs/"+order+"/assignment",map("operationId",UUID.randomUUID(),"version",node(report).get("version").asInt(),"technicianId",tech.id));status(200,assigned);
            status(200,tech.call(workflow,"POST","/api/repairs/"+order+"/start",map("operationId",UUID.randomUUID(),"version",node(assigned).get("version").asInt())));
            long ackDeadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();JsonNode repair=detail(owner,order);
            while(!repair.get("status").asText().equals("IN_PROGRESS")&&System.nanoTime()<ackDeadline){Thread.sleep(200);repair=detail(owner,order);}
            assertEquals("IN_PROGRESS",repair.get("status").asText(),"real M3 restriction ACK required before checking personal termination");
            var restriction=internal("/internal/resources/sources/M3/"+order);status(200,restriction);assertTrue(node(restriction).get("active").asBoolean(),"real B persisted the maintenance source");
            long end=System.nanoTime()+Duration.ofSeconds(20).toNanos();JsonNode personal=null;
            while(System.nanoTime()<end){var response=owner.call(resource,"GET","/api/reservations/"+bookingId,null);status(200,response);personal=node(response);if("CANCELLED".equals(personal.get("status").asText()))break;Thread.sleep(250);}
            assertNotNull(personal);assertEquals("CANCELLED",personal.get("status").asText(),"real M3-to-M1 maintenance broadcast must terminate the personal reservation");assertEquals("SYSTEM_MAINTENANCE",personal.get("cancelReason").asText());
            var released=internal("/internal/resources/sources/M1/"+bookingId);status(200,released);assertFalse(node(released).get("active").asBoolean(),"M1 releases its actual source without clearing M3");
            try(var c=DriverManager.getConnection(resourceDb,resourceUser,resourcePassword);var query=c.prepareStatement("SELECT count(*) FROM m1.reservation_violations WHERE reservation_id=?")){query.setObject(1,UUID.fromString(bookingId));try(var rows=query.executeQuery()){assertTrue(rows.next());assertEquals(0,rows.getLong(1),"maintenance must not insert a no-show violation");}}
            System.out.println("M03_M05_REAL_BROADCAST_PASS reservation="+bookingId+" repair="+order);
        }
        String bootstrapLogin;
        public void close()throws Exception{
            for(int i=processes.size()-1;i>=0;i--){Process p=processes.get(i);p.destroy();if(!p.waitFor(10,TimeUnit.SECONDS)){p.destroyForcibly();p.waitFor(5,TimeUnit.SECONDS);}}
            clients.forEach(Client::close);wire.close();
        }
    }
}
