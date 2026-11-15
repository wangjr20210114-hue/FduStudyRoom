package com.campus.notification;

import com.campus.common.security.IdentityPrincipal;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class NotificationController {
    private final NotificationStore store;private final SmtpTransport smtp;
    public NotificationController(NotificationStore store,SmtpTransport smtp){this.store=store;this.smtp=smtp;}
    @PostMapping("/internal/notifications/events")
    ResponseEntity<NotificationStore.Receipt> accept(@Valid @RequestBody NotificationEvent event){var result=store.accept(event,smtp.configured());return ResponseEntity.status(result.duplicate()?200:201).body(result);}
    @PostMapping("/internal/notifications/events/{eventId}/cancel")
    NotificationStore.Receipt cancel(@PathVariable UUID eventId){return store.cancel(eventId);}
    @GetMapping("/api/notifications") @PreAuthorize("hasAuthority('notification:read')")
    Map<String,Object> list(@AuthenticationPrincipal IdentityPrincipal principal,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestHeader(value="Accept-Language",defaultValue="zh-CN") String language){
        boolean english=false;try{var preferred=Locale.lookup(Locale.LanguageRange.parse(language),List.of(Locale.forLanguageTag("zh-CN"),Locale.ENGLISH));english=Locale.ENGLISH.equals(preferred);}catch(IllegalArgumentException ignored){}
        return store.list(principal.id(),page,size,english);
    }
    @PostMapping("/api/notifications/{notificationId}/read") @PreAuthorize("hasAuthority('notification:read')")
    ResponseEntity<Void> read(@AuthenticationPrincipal IdentityPrincipal principal,@PathVariable UUID notificationId){store.read(principal.id(),notificationId);return ResponseEntity.noContent().build();}
}
