package com.campus.notification;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class VerificationEmailController {
 private final VerificationEmailService service;public VerificationEmailController(VerificationEmailService service){this.service=service;}
 @PostMapping("/internal/notifications/verification-email") ResponseEntity<VerificationEmailService.Receipt> send(@Valid @RequestBody VerificationEmailRequest request){var receipt=service.deliver(request);return ResponseEntity.status("UNKNOWN".equals(receipt.status())?202:200).body(receipt);}
}
