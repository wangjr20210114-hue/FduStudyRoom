package com.campus.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ApiErrors {
    public record Body(String code, String message, String traceId) {}
    private final JsonMapper mapper;

    public ApiErrors(JsonMapper mapper) { this.mapper = mapper; }

    public Body body(HttpServletRequest request, String code) {
        return new Body(code, "The request could not be completed.", String.valueOf(request.getAttribute("traceId")));
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), body(request, code));
    }
}
