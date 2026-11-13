package com.campus.workflow;
import com.campus.common.error.ApiException;
import com.campus.common.security.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
@Component
public final class RemoteIdentityLookup implements IdentityLookup {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final URI endpoint;
    private final TokenService tokens;
    private final AuthSettings settings;
    public RemoteIdentityLookup(TokenService tokens, AuthSettings settings,
            @Value("${campus.workflow.identity-url:http://127.0.0.1:8080/internal/auth/introspect}") String endpoint) {
        this.tokens=tokens; this.settings=settings; this.endpoint=URI.create(endpoint);
        if (!java.util.Set.of("http", "https").contains(this.endpoint.getScheme()) || this.endpoint.getUserInfo()!=null)
            throw new IllegalArgumentException("Invalid identity service endpoint");
    }
    @Override public IdentityPrincipal resolve(TokenService.Claims claims) {
        try {
            var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(4))
                .header("X-Service-Key", settings.serviceKey())
                .header("Authorization", "Bearer " + tokens.issue(claims.userId(), claims.authVersion()))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
            var response=client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()==401 || response.statusCode()==403) throw new ApiException(401,"INVALID_SESSION");
            if (response.statusCode()!=200) throw new ApiException(503,"IDENTITY_UNAVAILABLE");
            var identity=JsonMapper.builder().build().readValue(response.body(), IdentityPrincipal.class);
            if (!identity.id().equals(claims.userId()) || identity.authVersion()!=claims.authVersion()) throw new ApiException(401,"INVALID_SESSION");
            return identity;
        } catch (ApiException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(503,"IDENTITY_UNAVAILABLE"); }
        catch (Exception e) { throw new ApiException(503,"IDENTITY_UNAVAILABLE"); }
    }
}
