package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.exception.StaffProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.StaffShiftRequest;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffShiftResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffShiftStaffOption;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffShiftAdminRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffShiftAdminService;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorWorkShiftJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitRoom;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitRoomService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffShiftAdminServiceImpl implements StaffShiftAdminService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final StaffShiftAdminRepository shifts;
    private final StaffAccountDirectory accounts;
    private final ManageVisitRoomService rooms;
    private final CoordinationGuardService guard;

    public StaffShiftAdminServiceImpl(StaffShiftAdminRepository shifts,
            StaffAccountDirectory accounts, ManageVisitRoomService rooms,
            CoordinationGuardService guard) {
        this.shifts = shifts; this.accounts = accounts; this.rooms = rooms; this.guard = guard;
    }

    @Transactional(readOnly = true)
    @Override
    public List<StaffShiftResponse> list(LocalDate from, LocalDate to, UUID staffId, String role, UUID roomId) {
        validateRange(from, to);
        Instant start = from.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        return shifts.findShifts(start, end, staffId, role, roomId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<StaffShiftResponse> listForAccount(LocalDate from, LocalDate to, UUID accountId, String role) {
        validateRange(from, to);
        var profile = shifts.findActiveStaffOption(accountId).orElseThrow(this::missing);
        if (!profile.getAccountType().equals(role)) throw this.missing();
        return list(from, to, profile.getId(), role, null);
    }

    @Transactional(readOnly = true)
    @Override
    public List<StaffShiftStaffOption> staffOptions() {
        return accounts.activeStaff().stream().map(account -> {
            var profile = shifts.findActiveStaffOption(account.id()).map(row -> new StaffShiftStaffOption(
                    row.getId().toString(), row.getDisplayName(), row.getAccountType().equals("DOCTOR")
                            ? "ORTHOPEDIC_DOCTOR" : row.getAccountType())).orElse(null);
            return profile;
        }).filter(option -> option != null).toList();
    }

    @Transactional
    @Override
    public StaffShiftResponse create(StaffShiftRequest request) {
        validate(request, true);
        guard.acquire();
        UUID id = UUID.randomUUID();
        Instant startsAt = at(request.date(), request.startTime());
        Instant endsAt = at(request.date(), request.endTime());
        ensureNoOverlap(request.staffId(), request.roomId(), startsAt, endsAt, null);
        shifts.save(new DoctorWorkShiftJpaEntity(id, request.staffId(), startsAt, endsAt, request.roomId()));
        return one(id);
    }

    @Transactional
    @Override
    public StaffShiftResponse update(UUID id, StaffShiftRequest request) {
        Existing existing = existing(id);
        if (!existing.startsAt().isAfter(Instant.now())) throw new StaffProfileException("STAFF_SHIFT_NOT_EDITABLE");
        UUID staffId = existing.staffId();
        if (request.staffId() != null && !request.staffId().equals(staffId)) throw invalid();
        request = new StaffShiftRequest(staffId, request.roomId(), request.date(), request.startTime(), request.endTime());
        validate(request, false);
        guard.acquire();
        Instant startsAt = at(request.date(), request.startTime());
        Instant endsAt = at(request.date(), request.endTime());
        ensureNoOverlap(staffId, request.roomId(), startsAt, endsAt, id);
        var shift = shifts.findNonDoctorShift(id).orElseThrow(this::missing);
        shift.updateInterval(startsAt, endsAt, request.roomId());
        shifts.save(shift);
        return one(id);
    }

    @Transactional
    @Override
    public void delete(UUID id) {
        Existing existing = existing(id);
        if (!existing.startsAt().isAfter(Instant.now())) throw new StaffProfileException("STAFF_SHIFT_NOT_EDITABLE");
        guard.acquire();
        shifts.delete(shifts.findNonDoctorShift(id).orElseThrow(this::missing));
    }

    private void validate(StaffShiftRequest r, boolean needsStaff) {
        if (r == null || (needsStaff && r.staffId() == null) || r.roomId() == null || r.date() == null
                || r.startTime() == null || r.endTime() == null || !r.startTime().isBefore(r.endTime())
                || r.date().isBefore(LocalDate.now(CLINIC_ZONE))) throw invalid();
        if (r.staffId() != null) {
            UUID accountId = shifts.findActiveStaffAccount(r.staffId()).map(
                    StaffShiftAdminRepository.StaffAccountRow::getAccountId).orElseThrow(this::missing);
            var account = accounts.activeStaff(accountId).orElseThrow(() -> new StaffProfileException("STAFF_ACCOUNT_UNAVAILABLE"));
            if (account.role().name().equals("DOCTOR")) throw invalid();
            String expectedRoomType = switch (account.role().name()) {
                case "RECEPTIONIST" -> "EXAMINATION";
                case "LAB_TECHNICIAN" -> "LABORATORY";
                case "IMAGING_TECHNICIAN" -> "IMAGING";
                default -> throw invalid();
            };
            VisitRoom room = rooms.get(r.roomId());
            if (!room.active() || !expectedRoomType.equals(room.type())) throw invalid();
        } else if (!rooms.get(r.roomId()).active()) {
            throw invalid();
        }
    }

    private void ensureNoOverlap(UUID staffId, UUID roomId, Instant start, Instant end, UUID ignoreId) {
        if (shifts.existsOverlappingShift(staffId, roomId, start, end, ignoreId))
            throw new StaffProfileException("STAFF_SHIFT_CONFLICT");
    }

    private StaffShiftResponse one(UUID id) {
        return shifts.findShiftRow(id).map(this::response).orElseThrow(this::missing);
    }

    private Existing existing(UUID id) {
        var found = shifts.findNonDoctorShift(id).orElseThrow(this::missing);
        return new Existing(found.doctorProfileId(), found.startsAt());
    }

    private StaffShiftResponse response(StaffShiftAdminRepository.ShiftRow row) {
        Instant start = row.getStartsAt();
        Instant end = row.getEndsAt();
        String dbRole = row.getAccountType();
        String role = dbRole.equals("DOCTOR") ? "ORTHOPEDIC_DOCTOR" : dbRole;
        List<String> actions = start.atZone(CLINIC_ZONE).isAfter(Instant.now().atZone(CLINIC_ZONE))
                ? List.of("edit", "delete") : List.of("view");
        return new StaffShiftResponse(row.getId().toString(), start.atZone(CLINIC_ZONE).toLocalDate().toString(),
                start.atZone(CLINIC_ZONE).toLocalTime().toString().substring(0,5),
                end.atZone(CLINIC_ZONE).toLocalTime().toString().substring(0,5),
                row.getStaffProfileId().toString(), row.getDisplayName(), role,
                row.getRoomId().toString(), row.getDisplayLabel(), row.getRoomType(), actions);
    }

    private Instant at(LocalDate date, LocalTime time) { return date.atTime(time).atZone(CLINIC_ZONE).toInstant(); }
    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || to.isAfter(from.plusDays(89))) throw invalid();
    }
    private StaffProfileException invalid() { return new StaffProfileException("STAFF_SHIFT_REQUEST_INVALID"); }
    private StaffProfileException missing() { return new StaffProfileException("STAFF_PROFILE_NOT_FOUND"); }
    private record Existing(UUID staffId, Instant startsAt) { }
}
