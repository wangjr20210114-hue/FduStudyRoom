package com.campus.notification;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record VerificationEmailRequest(@NotNull UUID operationId,@NotNull UUID userId,
 @NotBlank @Email @Size(max=254) @Pattern(regexp="[^\\r\\n\\s]+") String email,
 @NotBlank @Size(min=32,max=2048) @Pattern(regexp="[A-Za-z0-9._~-]+") String token,@NotNull Instant expiresAt) {
 @Override public String toString(){return "VerificationEmailRequest[redacted]";}
}
