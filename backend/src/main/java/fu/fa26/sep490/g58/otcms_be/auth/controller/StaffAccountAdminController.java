package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Staff-account creation stays in Auth; Staff Profiles only links existing accounts. */
@RestController
@RequestMapping("/api/v1/admin/staff-accounts")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class StaffAccountAdminController {
    public record Request(@NotBlank @Email String email, @NotBlank @Size(max = 100) String fullName,
            @NotNull AccountType role) { }
    private final StaffAccountAdminService accounts;

    public StaffAccountAdminController(StaffAccountAdminService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffAccountAdminService.Issued create(@Valid @RequestBody Request request) {
        return accounts.create(request.email(), request.fullName(), request.role());
    }
}
