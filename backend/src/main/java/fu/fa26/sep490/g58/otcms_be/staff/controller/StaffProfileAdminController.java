package fu.fa26.sep490.g58.otcms_be.staff.controller;

import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.staff.exception.StaffProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.StaffProfileInput;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfileResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfilesPageResponse;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffProfileAdminService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class StaffProfileAdminController {
    private final StaffProfileAdminService profiles;
    public StaffProfileAdminController(StaffProfileAdminService profiles) { this.profiles = profiles; }

    @GetMapping("/api/v1/admin/staff-profiles")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public StaffProfilesPageResponse list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String role, @RequestParam(required = false) String status,
            @RequestParam(name = "public", required = false) Boolean isPublic,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return profiles.list(q, role, status, isPublic, page, size);
    }

    @GetMapping("/api/v1/admin/staff-profiles/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public StaffProfileResponse detail(@PathVariable UUID id) { return profiles.detail(id); }

    @GetMapping("/api/v1/admin/staff-profiles/eligible-accounts")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<StaffAccountDirectory.Entry> eligible() { return profiles.eligible(); }

    @PostMapping(value = "/api/v1/admin/staff-profiles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffProfileResponse create(@RequestPart("data") StaffProfileInput input,
            @RequestPart(value = "photo", required = false) MultipartFile photo) throws IOException {
        return profiles.create(input, photo == null ? null : photo.getBytes(),
                photo == null ? null : photo.getContentType());
    }

    @PutMapping(value = "/api/v1/admin/staff-profiles/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public StaffProfileResponse update(@PathVariable UUID id, @RequestPart("data") StaffProfileInput input,
            @RequestPart(value = "photo", required = false) MultipartFile photo) throws IOException {
        return profiles.update(id, input, photo == null ? null : photo.getBytes(),
                photo == null ? null : photo.getContentType());
    }

    @DeleteMapping("/api/v1/admin/staff-profiles/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) { profiles.remove(id); }

    @PutMapping(value = "/api/v1/admin/staff-profiles/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public StaffProfileResponse photo(@PathVariable UUID id, @RequestPart("photo") MultipartFile photo) throws IOException {
        return profiles.savePhoto(id, photo.getBytes(), photo.getContentType());
    }

    @GetMapping("/api/v1/staff-profiles/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedAccount actor) {
        var profile = profiles.photo(id, actor != null && actor.accountRole() == AccountRole.ADMIN);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(profile.photoContentType()))
                .body(profile.photoContent());
    }
}
