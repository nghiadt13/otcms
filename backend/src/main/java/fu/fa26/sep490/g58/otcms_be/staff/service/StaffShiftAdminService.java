package fu.fa26.sep490.g58.otcms_be.staff.service;

import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.exception.StaffProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.StaffShiftRequest;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffShiftResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffShiftStaffOption;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitRoom;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitRoomService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffShiftAdminService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final JdbcTemplate jdbc;
    private final StaffAccountDirectory accounts;
    private final ManageVisitRoomService rooms;
    private final CoordinationGuardService guard;

    public StaffShiftAdminService(JdbcTemplate jdbc, StaffAccountDirectory accounts, ManageVisitRoomService rooms,
            CoordinationGuardService guard) {
        this.jdbc = jdbc; this.accounts = accounts; this.rooms = rooms; this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<StaffShiftResponse> list(LocalDate from, LocalDate to, UUID staffId, String role, UUID roomId) {
        validateRange(from, to);
        String sql = """
                SELECT s.id, s.staff_profile_id, p.display_name, a.account_type, s.room_id,
                       r.display_label, r.room_type, s.starts_at, s.ends_at
                FROM staff_work_shifts s JOIN staff_profiles p ON p.id=s.staff_profile_id
                JOIN accounts a ON a.id=p.account_id JOIN rooms r ON r.id=s.room_id
                WHERE s.starts_at < ? AND s.ends_at > ? AND p.status='ACTIVE'
                  AND a.status='ACTIVE' AND a.account_type IN ('RECEPTIONIST','LAB_TECHNICIAN','IMAGING_TECHNICIAN')
                  AND (?::uuid IS NULL OR s.staff_profile_id=?)
                  AND (?::varchar IS NULL OR a.account_type=?)
                  AND (?::uuid IS NULL OR s.room_id=?)
                ORDER BY s.starts_at, p.display_name
                """;
        Instant start = from.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        return jdbc.query(sql, (rs, n) -> response(rs), Timestamp.from(end), Timestamp.from(start),
                staffId, staffId, role, role, roomId, roomId);
    }

    @Transactional(readOnly = true)
    public List<StaffShiftStaffOption> staffOptions() {
        return accounts.activeStaff().stream().map(account -> {
            var profile = jdbc.query("SELECT id, display_name FROM staff_profiles WHERE account_id=? AND status='ACTIVE'",
                    rs -> rs.next() ? new StaffShiftStaffOption(rs.getString(1), rs.getString(2),
                            account.role().name().equals("DOCTOR") ? "ORTHOPEDIC_DOCTOR" : account.role().name()) : null,
                    account.id());
            return profile;
        }).filter(option -> option != null).toList();
    }

    @Transactional
    public StaffShiftResponse create(StaffShiftRequest request) {
        validate(request, true);
        guard.acquire();
        UUID id = UUID.randomUUID();
        Instant startsAt = at(request.date(), request.startTime());
        Instant endsAt = at(request.date(), request.endTime());
        ensureNoOverlap(request.staffId(), request.roomId(), startsAt, endsAt, null);
        jdbc.update("INSERT INTO staff_work_shifts(id,staff_profile_id,room_id,starts_at,ends_at) VALUES (?,?,?,?,?)",
                id, request.staffId(), request.roomId(), Timestamp.from(startsAt), Timestamp.from(endsAt));
        return one(id);
    }

    @Transactional
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
        int changed = jdbc.update("UPDATE staff_work_shifts SET staff_profile_id=?,room_id=?,starts_at=?,ends_at=?,updated_at=now() WHERE id=?",
                staffId, request.roomId(), Timestamp.from(startsAt), Timestamp.from(endsAt), id);
        if (changed == 0) throw missing();
        return one(id);
    }

    @Transactional
    public void delete(UUID id) {
        Existing existing = existing(id);
        if (!existing.startsAt().isAfter(Instant.now())) throw new StaffProfileException("STAFF_SHIFT_NOT_EDITABLE");
        guard.acquire();
        if (jdbc.update("DELETE FROM staff_work_shifts WHERE id=?", id) == 0) throw missing();
    }

    private void validate(StaffShiftRequest r, boolean needsStaff) {
        if (r == null || (needsStaff && r.staffId() == null) || r.roomId() == null || r.date() == null
                || r.startTime() == null || r.endTime() == null || !r.startTime().isBefore(r.endTime())
                || r.date().isBefore(LocalDate.now(CLINIC_ZONE))) throw invalid();
        if (r.staffId() != null) {
            UUID accountId = jdbc.query("SELECT account_id FROM staff_profiles WHERE id=? AND status='ACTIVE'",
                    rs -> rs.next() ? rs.getObject(1, UUID.class) : null, r.staffId());
            if (accountId == null) throw missing();
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
        Integer count = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM staff_work_shifts
                        WHERE (staff_profile_id=? OR room_id=?) AND starts_at < ? AND ends_at > ?
                          AND (?::uuid IS NULL OR id<>?))
                     + (SELECT count(*) FROM doctor_shifts WHERE room_id=? AND starts_at < ? AND ends_at > ?)
                """, Integer.class, staffId, roomId, Timestamp.from(end), Timestamp.from(start), ignoreId, ignoreId,
                roomId, Timestamp.from(end), Timestamp.from(start));
        if (count != null && count > 0) throw new StaffProfileException("STAFF_SHIFT_CONFLICT");
    }

    private StaffShiftResponse one(UUID id) {
        return jdbc.query("""
                SELECT s.id, s.staff_profile_id, p.display_name, a.account_type, s.room_id,
                       r.display_label, r.room_type, s.starts_at, s.ends_at
                FROM staff_work_shifts s JOIN staff_profiles p ON p.id=s.staff_profile_id
                JOIN accounts a ON a.id=p.account_id JOIN rooms r ON r.id=s.room_id WHERE s.id=?
                """, rs -> rs.next() ? response(rs) : null, id);
    }

    private Existing existing(UUID id) {
        Existing found = jdbc.query("SELECT staff_profile_id, starts_at FROM staff_work_shifts WHERE id=?",
                rs -> rs.next() ? new Existing(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()) : null, id);
        if (found == null) throw missing();
        return found;
    }

    private StaffShiftResponse response(ResultSet rs) throws SQLException {
        Instant start = rs.getTimestamp("starts_at").toInstant();
        Instant end = rs.getTimestamp("ends_at").toInstant();
        String dbRole = rs.getString("account_type");
        String role = dbRole.equals("DOCTOR") ? "ORTHOPEDIC_DOCTOR" : dbRole;
        List<String> actions = start.atZone(CLINIC_ZONE).isAfter(Instant.now().atZone(CLINIC_ZONE))
                ? List.of("edit", "delete") : List.of("view");
        return new StaffShiftResponse(rs.getString("id"), start.atZone(CLINIC_ZONE).toLocalDate().toString(),
                start.atZone(CLINIC_ZONE).toLocalTime().toString().substring(0,5),
                end.atZone(CLINIC_ZONE).toLocalTime().toString().substring(0,5),
                rs.getString("staff_profile_id"), rs.getString("display_name"), role,
                rs.getString("room_id"), rs.getString("display_label"), rs.getString("room_type"), actions);
    }

    private Instant at(LocalDate date, LocalTime time) { return date.atTime(time).atZone(CLINIC_ZONE).toInstant(); }
    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to) || to.isAfter(from.plusDays(89))) throw invalid();
    }
    private StaffProfileException invalid() { return new StaffProfileException("STAFF_SHIFT_REQUEST_INVALID"); }
    private StaffProfileException missing() { return new StaffProfileException("STAFF_PROFILE_NOT_FOUND"); }
    private record Existing(UUID staffId, Instant startsAt) { }
}
