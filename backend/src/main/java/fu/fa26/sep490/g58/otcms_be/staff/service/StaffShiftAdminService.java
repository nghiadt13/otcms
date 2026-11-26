package fu.fa26.sep490.g58.otcms_be.staff.service;

import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.StaffShiftRequest;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffShiftResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffShiftStaffOption;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StaffShiftAdminService {
    List<StaffShiftResponse> list(LocalDate from, LocalDate to, UUID staffId, String role, UUID roomId);
    List<StaffShiftResponse> listForAccount(LocalDate from, LocalDate to, UUID accountId, String role);
    List<StaffShiftStaffOption> staffOptions();
    StaffShiftResponse create(StaffShiftRequest request);
    StaffShiftResponse update(UUID id, StaffShiftRequest request);
    void delete(UUID id);
}
