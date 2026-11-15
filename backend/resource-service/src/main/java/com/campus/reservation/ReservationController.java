package com.campus.reservation;
import com.campus.common.security.IdentityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReservationController {
 private final ReservationService service;
 public ReservationController(ReservationService service){this.service=service;}
 public record Create(@NotNull UUID operationId,@NotNull UUID seatId,@NotNull Instant startsAt,@NotNull Instant endsAt){}
 public record Cancel(@NotNull UUID operationId){}
 public record AdminCancel(@NotNull UUID operationId,@NotBlank @Size(max=500) String reason){}
 public record Checkin(@NotNull UUID operationId,@NotBlank @Pattern(regexp="[0-9]{6}") String code){@Override public String toString(){return "Checkin[REDACTED]";}}
 public record Maintenance(@NotNull UUID operationId,@NotNull UUID sourceId,@NotNull @Min(1) Integer sourceVersion,@NotNull UUID roomId,@NotNull Boolean active,@NotBlank String effectiveAt){}
 private ResponseEntity<?> response(ReservationService.Write result,boolean creating){String state=String.valueOf(result.reservation().get("status"));int status=Set.of("PENDING","CANCEL_PENDING").contains(state)?202:creating&&!result.duplicate()?201:200;return ResponseEntity.status(status).body(result.reservation());}
 @GetMapping("/api/reservations/availability") @PreAuthorize("hasAuthority('reservation:read')")
 public Object availability(@AuthenticationPrincipal IdentityPrincipal user,@RequestParam Instant startsAt,@RequestParam Instant endsAt,@RequestParam(required=false) String campus,@RequestParam(required=false) String building,@RequestParam(required=false) Boolean hasSocket,@RequestParam(required=false) UUID roomId){return service.availability(user,startsAt,endsAt,campus,building,hasSocket,roomId);}
 @PostMapping("/api/reservations") @PreAuthorize("hasAuthority('reservation:create')")
 public Object create(@AuthenticationPrincipal IdentityPrincipal user,@Valid @RequestBody Create body){return response(service.create(user,body.operationId(),body.seatId(),body.startsAt(),body.endsAt()),true);}
 @GetMapping("/api/reservations/{id}") @PreAuthorize("hasAuthority('reservation:read')")
 public Object detail(@AuthenticationPrincipal IdentityPrincipal user,@PathVariable UUID id){return service.detail(user,id);}
 @PostMapping("/api/reservations/{id}/cancel") @PreAuthorize("hasAuthority('reservation:cancel')")
 public Object cancel(@AuthenticationPrincipal IdentityPrincipal user,@PathVariable UUID id,@Valid @RequestBody Cancel body){return response(service.cancel(user,id,body.operationId(),null,false),false);}
 @PostMapping("/api/reservations/{id}/admin-cancel") @PreAuthorize("hasAuthority('reservation:admin-cancel')")
 public Object adminCancel(@AuthenticationPrincipal IdentityPrincipal user,@PathVariable UUID id,@Valid @RequestBody AdminCancel body){return response(service.cancel(user,id,body.operationId(),body.reason(),true),false);}
 @PostMapping("/api/reservations/{id}/check-in") @PreAuthorize("hasAuthority('reservation:checkin')")
 public Object checkin(@AuthenticationPrincipal IdentityPrincipal user,@PathVariable UUID id,@Valid @RequestBody Checkin body){return response(service.checkin(user,id,body.operationId(),body.code()),false);}
 @GetMapping("/api/reservations/{id}/rebook") @PreAuthorize("hasAuthority('reservation:create')")
 public Object rebook(@AuthenticationPrincipal IdentityPrincipal user,@PathVariable UUID id){return service.rebook(user,id);}
 @GetMapping("/api/reservations") @PreAuthorize("hasAuthority('reservation:read')")
 public Object history(@AuthenticationPrincipal IdentityPrincipal user,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false)String cursor,@RequestParam(required=false)UUID roomId,@RequestParam(required=false)UUID seatId,@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to){return service.history(user,false,limit,cursor,roomId,seatId,from,to);}
 @GetMapping("/api/admin/reservations") @PreAuthorize("hasAuthority('reservation:admin-read')")
 public Object management(@AuthenticationPrincipal IdentityPrincipal user,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false)String cursor,@RequestParam(required=false)UUID roomId,@RequestParam(required=false)UUID seatId,@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to){return service.history(user,true,limit,cursor,roomId,seatId,from,to);}
 @PostMapping("/internal/reservations/maintenance-events")
 public Object maintenance(@Valid @RequestBody Maintenance body){return service.maintenance(body.operationId(),body.sourceId(),body.sourceVersion(),body.roomId(),body.active(),body.effectiveAt());}
 @GetMapping("/internal/reservations/statistics")
 public Object statistics(@RequestParam Instant from,@RequestParam Instant to,@RequestParam Instant asOf,@RequestParam(required=false) UUID roomId){return service.statistics(from,to,asOf,roomId);}
}
