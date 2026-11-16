package com.campus.resource.application;
import com.campus.common.error.ApiException;
import com.campus.common.security.AuthSettings;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service public class ResourceEligibility {
    private final String origin,key;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public ResourceEligibility(AuthSettings settings,@Value("${campus.resource.identity-url:http://127.0.0.1:${server.port:8080}}")String origin){
        this.origin=origin;
        this.key=settings.serviceKey();
    }
    public Map<String,Object> get(UUID id){
        if(id==null)throw new ApiException(400,"USER_REQUIRED");
        try{
            var request=HttpRequest.newBuilder(URI.create(origin+"/internal/identity/users/"+id+"/eligibility")).timeout(Duration.ofSeconds(5)).header("X-Service-Key",key).GET().build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new ApiException(503,"IDENTITY_UNAVAILABLE");
            var user=ResourceStore.read(response.body());
            if(!id.toString().equals(ResourceStore.text(user,"userId"))||!ResourceStore.bool(user,"enabled",false))throw new ApiException(403,"USER_NOT_ELIGIBLE");
            return user;
        }
        catch(ApiException e){
            throw e;
        }
        catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new ApiException(503,"IDENTITY_UNAVAILABLE");
        }
        catch(Exception e){
            throw new ApiException(503,"IDENTITY_UNAVAILABLE");
        }
    }
}
