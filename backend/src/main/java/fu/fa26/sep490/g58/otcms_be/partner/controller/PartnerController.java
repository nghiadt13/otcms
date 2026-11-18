package fu.fa26.sep490.g58.otcms_be.partner.controller;

import fu.fa26.sep490.g58.otcms_be.partner.service.PartnerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class PartnerController {
    private final PartnerService partners;
    public PartnerController(PartnerService partners) { this.partners = partners; }
    public record Request(@NotBlank @Size(max = 255) String name, @Size(max = 255) String contact,
            @NotNull Boolean active, @jakarta.validation.constraints.NotEmpty java.util.List<String> categoryCodes) { }
    @GetMapping("/api/v1/admin/partners") @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, ?> list() { return Map.of("items", partners.list(false, null)); }
    @GetMapping("/api/v1/admin/partner-categories") @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, ?> categories() { return Map.of("items", partners.categories()); }
    @GetMapping("/api/v1/laboratory/partners") @PreAuthorize("hasAuthority('ROLE_LAB_TECHNICIAN')")
    public Map<String, ?> active() { return Map.of("items", partners.list(true, "LABORATORY")); }
    @PostMapping("/api/v1/admin/partners") @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public PartnerService.Partner create(@Valid @RequestBody Request request) { return partners.create(request.name(), request.contact(), request.categoryCodes()); }
    @PutMapping("/api/v1/admin/partners/{id}") @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public PartnerService.Partner update(@PathVariable UUID id, @Valid @RequestBody Request request) { return partners.update(id, request.name(), request.contact(), request.active(), request.categoryCodes()); }
}
