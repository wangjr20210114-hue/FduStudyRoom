package com.campus.identity.api;
import com.campus.identity.application.SystemSettingsService;import java.util.List;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;
@RestController
public class SystemSettingsController {
 private final SystemSettingsService service;public SystemSettingsController(SystemSettingsService service){this.service=service;}
 @GetMapping("/internal/system/settings") public SystemSettingsService.Settings internal(){return service.current();}
 @GetMapping("/api/admin/settings") @PreAuthorize("hasAuthority('identity:settings:read')") public SystemSettingsService.Settings current(){return service.current();}
 @PutMapping("/api/admin/settings") @PreAuthorize("hasAuthority('identity:settings:write')") public SystemSettingsService.Settings update(@RequestBody SystemSettingsService.Update value){return service.update(value);}
 @GetMapping("/api/admin/settings/history") @PreAuthorize("hasAuthority('identity:settings:read')") public List<SystemSettingsService.History> history(){return service.history();}
}
