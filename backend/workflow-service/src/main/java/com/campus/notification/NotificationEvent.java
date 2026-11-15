package com.campus.notification;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record NotificationEvent(@NotNull UUID eventId,@NotNull UUID recipientId,
        @NotBlank @Pattern(regexp="M1|M2|M3|D") String sourceModule,@NotNull UUID sourceId,
        @NotBlank @Size(max=255) String title,@NotBlank @Size(max=4000) String body,
        @NotBlank @Size(max=255) String titleEn,@NotBlank @Size(max=4000) String bodyEn,
        @NotNull Instant scheduledAt,@Email @Size(max=254) String emailAddress) {}
