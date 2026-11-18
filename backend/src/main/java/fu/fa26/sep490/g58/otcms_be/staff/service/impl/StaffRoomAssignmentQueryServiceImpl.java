package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffRoomAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorWorkShiftRepository;
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
    private final DoctorWorkShiftRepository workShifts;
    public StaffRoomAssignmentQueryServiceImpl(StaffAccountDirectory accounts, StaffRoomAssignmentRepository assignments,
            DoctorWorkShiftRepository workShifts) {
        this.accounts = accounts;
        this.assignments = assignments;
        this.workShifts = workShifts;
    }
    @Override
    public java.util.Set<UUID> roomsWithActiveImagingTechnician(java.util.Collection<UUID> roomIds, Instant at) {
        if (roomIds == null || roomIds.isEmpty() || at == null) return java.util.Set.of();
        var rows = assignments.findCurrentAssignments(roomIds, at);
        var active = accounts.activeStaff(rows.stream().map(StaffRoomAssignmentRepository.Assignment::accountId).distinct().toList())
                .stream().filter(account -> account.role() == AccountType.IMAGING_TECHNICIAN)
                .map(StaffAccountDirectory.Entry::id).collect(java.util.stream.Collectors.toSet());
        return rows.stream().filter(row -> active.contains(row.accountId())).map(StaffRoomAssignmentRepository.Assignment::roomId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override public boolean isImagingTechnicianAssigned(UUID accountId, UUID roomId, Instant at) {
        if (accountId == null || roomId == null || at == null) return false;
        var account = accounts.activeStaff(accountId);
        return account.filter(a -> accountId.equals(a.id()) && a.role() == AccountType.IMAGING_TECHNICIAN).isPresent()
                && assignments.hasActiveAssignment(accountId, roomId, at);
    }

    @Override public boolean isLabTechnicianAssigned(UUID accountId, UUID roomId, Instant at) {
        if (accountId == null || roomId == null || at == null) return false;
        var account = accounts.activeStaff(accountId);
        return account.filter(a -> accountId.equals(a.id()) && a.role() == AccountType.LAB_TECHNICIAN).isPresent()
                && workShifts.hasActiveRoomAssignment(accountId, roomId, at);
    }

    @Override public boolean hasActiveLabShift(UUID accountId, Instant at) {
        if (accountId == null || at == null) return false;
        return accounts.activeStaff(accountId).filter(a -> a.role() == AccountType.LAB_TECHNICIAN).isPresent()
                && workShifts.hasActiveRoomShift(accountId, at);
    }
}
