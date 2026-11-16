package com.campus.statistics;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
public class StatisticsController {
 private final StatisticsService service;public StatisticsController(StatisticsService service){this.service=service;}
 @GetMapping("/api/statistics") @PreAuthorize("hasAuthority('statistics:read')")
 Map<String,Object> statistics(@RequestParam Instant from,@RequestParam Instant to,@RequestParam Instant asOf,@RequestParam(required=false) UUID roomId){return service.calculate(from,to,asOf,roomId);}
}
