package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffRoomAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffRoomAssignmentQueryService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StaffRoomAssignmentQueryServiceImpl implements StaffRoomAssignmentQueryService {
    private final StaffAccountDirectory accounts;
    private final StaffRoomAssignmentRepository assignments;
    public StaffRoomAssignmentQueryServiceImpl(StaffAccountDirectory accounts, StaffRoomAssignmentRepository assignments) {
        this.accounts = accounts;
        this.assignments = assignments;
    }
    @Override public boolean isImagingTechnicianAssigned(UUID accountId, UUID roomId, Instant at) {
        if (accountId == null || roomId == null || at == null) return false;
        var account = accounts.activeStaff(accountId);
        return account.filter(a -> accountId.equals(a.id()) && a.role() == AccountType.IMAGING_TECHNICIAN).isPresent()
                && assignments.hasActiveAssignment(accountId, roomId, at);
    }
}
