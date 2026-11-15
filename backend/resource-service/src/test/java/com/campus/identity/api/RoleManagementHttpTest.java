package com.campus.identity.api;

import com.campus.ResourceApplication;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoleManagementHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "isolated-role-fixture-password";
    private ConfigurableApplicationContext app;
    private JdbcTemplate jdbc;
    private HttpClient admin;
    private String base;

    @BeforeAll void startRealApplicationAndPostgres() throws Exception {
        var application = new SpringApplication(ResourceApplication.class);
        String adminName = "admin-" + UUID.randomUUID().toString().substring(0,8);
        app = application.run("--spring.profiles.active=local", "--server.address=127.0.0.1", "--server.port=0",
                "--campus.auth.secure-cookies=false", "--campus.auth.signing-key=" + Base64.getEncoder().encodeToString(new byte[32]),
                "--campus.auth.service-key=" + UUID.randomUUID(), "--campus.bootstrap-admin.login-name=" + adminName,
                "--campus.bootstrap-admin.password=" + PASSWORD);
        jdbc = app.getBean(JdbcTemplate.class);
        base = "http://127.0.0.1:" + app.getEnvironment().getProperty("local.server.port");
        admin = login(adminName);
    }

    @AfterAll void closeApplication() { if (app != null) app.close(); }

    @Test void canCreateRoleWithExactPermissionsAndAssignIt() throws Exception {
        String code = code();
        var response = request(admin, "POST", "/api/admin/roles", Map.of("code",code,"name","Room manager","permissions",List.of("resource:rooms:read","resource:rooms:write")));
        assertThat(response.statusCode()).as("role creation must succeed").isEqualTo(201);
        UUID roleId = UUID.fromString(tree(response).get("id").asText());
        assertThat(jdbc.queryForList("SELECT p.code FROM iam.role_permissions rp JOIN iam.permissions p ON p.id=rp.permission_id WHERE rp.role_id=?",String.class,roleId))
                .containsExactlyInAnyOrder("resource:rooms:read","resource:rooms:write");
        String login = fixtureUser(null);
        UUID id = jdbc.queryForObject("SELECT id FROM iam.users WHERE login_name=?",UUID.class,login);
        assertThat(request(admin,"PATCH","/api/admin/users/"+id+"/access",Map.of("roleId",roleId,"enabled",true,"expectedAuthVersion",1)).statusCode()).isEqualTo(204);
        var assigned = request(login(login),"GET","/api/auth/me",null);
        assertThat(assigned.statusCode()).isEqualTo(200);
        assertThat(tree(assigned).get("role").asText()).isEqualTo(code);
        assertThat(strings(tree(assigned).get("permissions"))).containsExactlyInAnyOrder("resource:rooms:read","resource:rooms:write");
    }

    @Test void rejectsUnknownPermissionWithoutPartialRole() throws Exception {
        String code = code();
        var response=request(admin,"POST","/api/admin/roles",Map.of("code",code,"name","Invalid","permissions",List.of("missing:permission")));
        assertThat(response.statusCode()).as("unknown permission rejected").isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.roles WHERE code=?",Integer.class,code)).isZero();
    }

    @Test void permissionCatalogueContainsPublishedModuleCodes() throws Exception {
        var response=request(admin,"GET","/api/admin/permissions",null);
        assertThat(response.statusCode()).as("permission catalogue available").isEqualTo(200);
        Set<String> codes=new HashSet<>(); tree(response).forEach(item -> codes.add(item.get("code").asText()));
        assertThat(codes).contains("identity:roles:read","identity:roles:write","resource:rooms:read","resource:rooms:write","resource:checkin-codes:read",
                "reservation:read","approval:read","approval:decide","repair:history:read","repair:accept","repair:evaluate","notification:read");
    }

    @Test void ordinaryUserCannotManageRolesOrChangeData() throws Exception {
        HttpClient user=login(fixtureUser(null)); String code=code(); UUID role=fixtureRole(Set.of("identity:users:read"));
        assertThat(request(user,"POST","/api/admin/roles",Map.of("code",code,"name","Escalation","permissions",List.of("identity:roles:write"))).statusCode())
                .as("ordinary user cannot create roles").isEqualTo(403);
        assertThat(request(user,"PATCH","/api/admin/roles/"+role+"/enabled",Map.of("enabled",false)).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.roles WHERE code=?",Integer.class,code)).isZero();
        assertThat(jdbc.queryForObject("SELECT enabled FROM iam.roles WHERE id=?",Boolean.class,role)).isTrue();
    }

    @Test void customRoleUsesGrantedPermissionRatherThanRoleName() throws Exception {
        UUID role=fixtureRole(Set.of("identity:roles:read"));HttpClient custom=login(fixtureUser(role));
        assertThat(request(custom,"GET","/api/admin/roles",null).statusCode()).as("custom role can use its granted permission").isEqualTo(200);
        assertThat(request(custom,"GET","/api/admin/users",null).statusCode()).as("ungranted user administration remains forbidden").isEqualTo(403);
    }

    @Test void disabledRoleRejectsExistingSessionAndPreservesReferences() throws Exception {
        UUID role=fixtureRole(Set.of("identity:users:read"));String name=fixtureUser(role);HttpClient custom=login(name);
        assertThat(request(custom,"GET","/api/admin/users",null).statusCode()).isEqualTo(200);
        assertThat(request(admin,"PATCH","/api/admin/roles/"+role+"/enabled",Map.of("enabled",false)).statusCode()).as("role disabling is supported").isEqualTo(204);
        assertThat(request(custom,"GET","/api/admin/users",null).statusCode()).as("old session cannot use disabled role").isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT role_id FROM iam.users WHERE login_name=?",UUID.class,name)).isEqualTo(role);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.roles WHERE id=?",Integer.class,role)).isEqualTo(1);
        assertThat(request(freshClient(),"POST","/api/auth/login",Map.of("loginName",name,"password",PASSWORD)).statusCode()).isEqualTo(401);
    }

    @Test void permissionRemovalAffectsTheNextRequest() throws Exception {
        UUID role=fixtureRole(Set.of("identity:users:read"));HttpClient custom=login(fixtureUser(role));
        assertThat(request(custom,"GET","/api/admin/users",null).statusCode()).isEqualTo(200);
        assertThat(request(admin,"PUT","/api/admin/roles/"+role+"/permissions",Map.of("permissions",List.of())).statusCode()).as("role permission update is supported").isEqualTo(204);
        assertThat(request(custom,"GET","/api/admin/users",null).statusCode()).as("revoked permission denied on next request").isEqualTo(403);
        assertThat(strings(tree(request(custom,"GET","/api/auth/me",null)).get("permissions"))).isEmpty();
    }

    private UUID fixtureRole(Set<String> permissions) {
        UUID role=UUID.randomUUID();jdbc.update("INSERT INTO iam.roles(id,code,name) VALUES(?,?,?)",role,code(),"Isolated custom role");
        for(String permission:permissions) jdbc.update("INSERT INTO iam.role_permissions(role_id,permission_id) SELECT ?,id FROM iam.permissions WHERE code=?",role,permission);
        return role;
    }
    private String fixtureUser(UUID role) {
        String name="user-"+UUID.randomUUID().toString().substring(0,8);
        if(role==null) role=jdbc.queryForObject("SELECT id FROM iam.roles WHERE code='USER'",UUID.class);
        jdbc.update("INSERT INTO iam.users(login_name,display_name,password_hash,role_id) VALUES(?,?,?,?)",name,name,app.getBean(PasswordEncoder.class).encode(PASSWORD),role);
        return name;
    }
    private HttpClient login(String name) throws Exception {
        HttpClient client=freshClient();var response=request(client,"POST","/api/auth/login",Map.of("loginName",name,"password",PASSWORD));
        assertThat(response.statusCode()).as("isolated account login").isEqualTo(200);return client;
    }
    private static HttpClient freshClient() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build(); }
    private HttpResponse<String> request(HttpClient client,String method,String path,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20));
        if(!method.equals("GET")) {
            var csrf=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
            JsonNode token=JSON.readTree(csrf.body());builder.header(token.get("headerName").asText(),token.get("token").asText());
        }
        builder.header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    private static JsonNode tree(HttpResponse<String> response) { return JSON.readTree(response.body()); }
    private static Set<String> strings(JsonNode array) { Set<String> result=new HashSet<>();array.forEach(x->result.add(x.asText()));return result; }
    private static String code() { return "CUSTOM_"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT); }
}
