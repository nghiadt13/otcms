package fu.fa26.sep490.g58.otcms_be.staff.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffShiftResponse;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffShiftAdminService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffShiftSelfController {
    private final StaffShiftAdminService shifts;

    public StaffShiftSelfController(StaffShiftAdminService shifts) {
        this.shifts = shifts;
    }

    @GetMapping("/api/v1/me/staff-shifts")
    @PreAuthorize("hasAuthority('ROLE_LAB_TECHNICIAN')")
    public ShiftItems ownShifts(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return new ShiftItems(shifts.listForAccount(from, to, actor.accountId(), actor.accountRole().name()));
    }

    public record ShiftItems(List<StaffShiftResponse> items) { }
}
