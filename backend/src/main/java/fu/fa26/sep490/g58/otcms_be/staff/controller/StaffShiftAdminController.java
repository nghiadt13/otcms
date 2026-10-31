package fu.fa26.sep490.g58.otcms_be.staff.controller;

import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.StaffShiftRequest;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffShiftResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffShiftStaffOption;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffShiftAdminService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class StaffShiftAdminController {
    private final StaffShiftAdminService shifts;
    public StaffShiftAdminController(StaffShiftAdminService shifts) { this.shifts = shifts; }

    @GetMapping("/api/v1/admin/staff-shifts")
    public ShiftItems list(@RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) UUID staffId, @RequestParam(required = false) String role,
            @RequestParam(required = false) UUID roomId) {
        return new ShiftItems(shifts.list(from, to, staffId, role, roomId));
    }

    @GetMapping("/api/v1/admin/staff-shifts/staff-options")
    public StaffOptions staffOptions() { return new StaffOptions(shifts.staffOptions()); }

    @PostMapping("/api/v1/admin/staff-shifts")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffShiftResponse create(@Valid @RequestBody StaffShiftRequest request) { return shifts.create(request); }

    @PutMapping("/api/v1/admin/staff-shifts/{id}")
    public StaffShiftResponse update(@PathVariable UUID id, @Valid @RequestBody StaffShiftRequest request) {
        return shifts.update(id, request);
    }

    @DeleteMapping("/api/v1/admin/staff-shifts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { shifts.delete(id); }

    public record ShiftItems(List<StaffShiftResponse> items) { }
    public record StaffOptions(List<StaffShiftStaffOption> items) { }
}
