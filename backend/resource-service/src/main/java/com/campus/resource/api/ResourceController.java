package com.campus.resource.api;
import com.campus.resource.application.*;
import java.util.*;
import java.time.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController public class ResourceController {
    private final ResourceCatalogue catalogue;
    private final ResourceOperations operations;
    private final ResourceStatistics statistics;
    public ResourceController(ResourceCatalogue catalogue,ResourceOperations operations,ResourceStatistics statistics){
        this.catalogue=catalogue;
        this.operations=operations;
        this.statistics=statistics;
    }
    @GetMapping("/api/resources/rooms") @PreAuthorize("hasAuthority('resource:rooms:read')") public Object rooms(){
        return catalogue.rooms();
    }
    @PostMapping("/api/resources/rooms") @PreAuthorize("hasAuthority('resource:rooms:write')") public ResponseEntity<?> room(@RequestBody Map<String,Object>body){
        return ResponseEntity.status(201).body(catalogue.saveRoom(null,body));
    }
    @PutMapping("/api/resources/rooms/{id}") @PreAuthorize("hasAuthority('resource:rooms:write')") public Object room(@PathVariable UUID id,@RequestBody Map<String,Object>body){
        return catalogue.saveRoom(id,body);
    }
    @GetMapping("/api/resources/rooms/{id}/seats") @PreAuthorize("hasAuthority('resource:rooms:read')") public Object seats(@PathVariable UUID id,@RequestParam(required=false)Boolean hasSocket){
        return catalogue.seats(id,hasSocket);
    }
    @PostMapping("/api/resources/rooms/{id}/seats") @PreAuthorize("hasAuthority('resource:rooms:write')") public ResponseEntity<?> seat(@PathVariable UUID id,@RequestBody Map<String,Object>body){
        return ResponseEntity.status(201).body(catalogue.saveSeat(id,null,body));
    }
    @PutMapping("/api/resources/seats/{id}") @PreAuthorize("hasAuthority('resource:rooms:write')") public Object seatUpdate(@PathVariable UUID id,@RequestBody Map<String,Object>body){
        return catalogue.saveSeat(null,id,body);
    }
    @GetMapping("/internal/resources/rooms") public Object trustedRooms(){
        return catalogue.rooms();
    }
    @GetMapping("/internal/resources/rooms/{id}") public Object trustedRoom(@PathVariable UUID id){
        return catalogue.details(id);
    }
    @GetMapping("/internal/resources/rooms/{id}/seats") public Object trustedSeats(@PathVariable UUID id,@RequestParam(required=false)Boolean hasSocket){
        return catalogue.seats(id,hasSocket);
    }
    @GetMapping("/internal/resources/seats/{id}") public Object trustedSeat(@PathVariable UUID id){
        return catalogue.seatDetails(id);
    }
    ResponseEntity<?> response(ResourceOperations.Write w){
        return ResponseEntity.status(w.duplicate()?200:201).body(w.result());
    }
    @PostMapping("/internal/resources/allocations") public Object allocate(@RequestBody Map<String,Object>b){
        return response(operations.allocate(b));
    }
    @PostMapping("/internal/resources/releases") public Object release(@RequestBody Map<String,Object>b){
        return response(operations.release(b));
    }
    @PutMapping("/internal/resources/restrictions/{id}") public Object restrict(@PathVariable UUID id,@RequestBody Map<String,Object>b){
        return response(operations.restrict(id,b));
    }
    @GetMapping("/internal/resources/sources/{module}/{id}") public Object source(@PathVariable String module,@PathVariable UUID id){
        return operations.source(module,id);
    }
    @GetMapping("/internal/resources/availability") public Object availability(@RequestParam Instant startsAt,@RequestParam Instant endsAt,@RequestParam UUID userId,@RequestParam(required=false)String campus,@RequestParam(required=false)String building,@RequestParam(required=false)Boolean hasSocket){
        return operations.availability(startsAt,endsAt,userId,campus,building,hasSocket);
    }
    @GetMapping("/internal/resources/statistics") public Object statistics(@RequestParam Instant from,@RequestParam Instant to,@RequestParam Instant asOf,@RequestParam(required=false)UUID roomId){
        return statistics.calculate(from,to,asOf,roomId);
    }
    @GetMapping("/internal/resources/capacity") public Object capacity(@RequestParam Instant startsAt,@RequestParam Instant endsAt,@RequestParam(required=false)Instant asOf,@RequestParam(required=false)UUID roomId){
        return asOf==null?statistics.planned(startsAt,endsAt,roomId):statistics.calculate(startsAt,endsAt,asOf,roomId);
    }
}
