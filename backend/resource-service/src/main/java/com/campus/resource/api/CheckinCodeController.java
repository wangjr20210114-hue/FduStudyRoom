package com.campus.resource.api;

import com.campus.resource.application.CheckinCodeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class CheckinCodeController {
    private final CheckinCodeService codes;
    public CheckinCodeController(CheckinCodeService codes){this.codes=codes;}
    @GetMapping("/api/resources/rooms/{roomId}/checkin-code")
    @PreAuthorize("hasAuthority('resource:checkin-codes:read')")
    public ResponseEntity<CheckinCodeService.Display> display(@PathVariable UUID roomId){
        return ResponseEntity.ok().header("Cache-Control","no-store").header("Pragma","no-cache").body(codes.display(roomId));
    }
    @GetMapping("/api/resources/rooms/{roomId}")
    @PreAuthorize("hasAuthority('resource:rooms:read')")
    public CheckinCodeService.RoomDetails room(@PathVariable UUID roomId){return codes.details(roomId);}
    public record VerifyRequest(@NotNull UUID roomId,@NotNull LocalDate date,@NotBlank @Pattern(regexp="[0-9]{6}") String code){
        @Override public String toString(){return "VerifyRequest[REDACTED]";}
    }
    @PostMapping("/internal/resources/checkin-codes/verify")
    public ResponseEntity<Map<String,Boolean>> verify(@Valid @RequestBody VerifyRequest input){
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("valid",codes.verify(input.roomId(),input.date(),input.code())));
    }
}
