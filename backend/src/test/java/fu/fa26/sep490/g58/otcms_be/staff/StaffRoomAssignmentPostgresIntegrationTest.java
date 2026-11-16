package fu.fa26.sep490.g58.otcms_be.staff;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffRoomAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffShiftAdminService;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.StaffShiftRequest;
import fu.fa26.sep490.g58.otcms_be.staff.exception.StaffProfileException;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@LocalPostgresTest
@Transactional
class StaffRoomAssignmentPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired StaffRoomAssignmentQueryService assignments;
    @Autowired fu.fa26.sep490.g58.otcms_be.staff.repository.StaffRoomAssignmentRepository assignmentRows;
    @Autowired StaffShiftAdminService shifts;

    @Test void doctorShiftIsExcludedFromStaffAssignmentsAndCannotBeChanged() {
        UUID account = UUID.randomUUID(), profile = UUID.randomUUID(), room = UUID.randomUUID(), shift = UUID.randomUUID();
        var date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2);
        var start = date.atTime(9, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'DOCTOR','ACTIVE',now(),now())", account, account + "@assignment.test");
        jdbc.update("insert into staff_profiles(id,account_id,display_name,work_contact,is_doctor) values (?,?,'Doctor','',true)", profile, account);
        jdbc.update("insert into rooms(id,room_code,display_label,room_type) values (?,?,'Doctor room','EXAMINATION')", room, "DOC-" + room);
        jdbc.update("insert into work_shifts(id,staff_profile_id,room_id,starts_at,ends_at) values (?,?,?,?,?)", shift, profile, room, Timestamp.from(start), Timestamp.from(start.plusSeconds(3600)));
        assertTrue(shifts.list(date, date, null, null, null).stream().noneMatch(item -> item.id().equals(shift.toString())));
        assertFalse(assignments.isImagingTechnicianAssigned(account, room, start));
        assertTrue(assignments.roomsWithActiveImagingTechnician(java.util.List.of(room), start).isEmpty());
        assertFalse(assignmentRows.hasActiveAssignment(account, room, start));
        assertTrue(assignmentRows.findCurrentAssignments(java.util.List.of(room), start).isEmpty());
        var request = new StaffShiftRequest(profile, room, date, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(10, 0));
        assertThrows(StaffProfileException.class, () -> shifts.update(shift, request));
        assertThrows(StaffProfileException.class, () -> shifts.delete(shift));
        assertEquals(1, jdbc.queryForObject("select count(*) from work_shifts where id=?", Integer.class, shift));
    }
    @Test void shiftBoundariesRoomIdentityProfileAndAccountStatusAreAuthoritative() {
        UUID actor = UUID.randomUUID(), profile = UUID.randomUUID(), room = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-05T03:00:00Z"), end = start.plusSeconds(3600);
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'IMAGING_TECHNICIAN','ACTIVE',now(),now())", actor, actor + "@assignment.test");
        jdbc.update("insert into staff_profiles(id,account_id,display_name,work_contact) values (?,?,'Shift technician','')", profile, actor);
        jdbc.update("insert into rooms(id,room_code,display_label,room_type) values (?,?,'Assignment room','IMAGING')", room, "ASSIGN-" + room);
        jdbc.update("insert into work_shifts(staff_profile_id,room_id,starts_at,ends_at) values (?,?,?,?)", profile, room, Timestamp.from(start), Timestamp.from(end));
        assertFalse(assignments.isImagingTechnicianAssigned(actor, room, start.minusSeconds(1)));
        assertTrue(assignments.isImagingTechnicianAssigned(actor, room, start));
        assertTrue(assignments.roomsWithActiveImagingTechnician(java.util.List.of(room), start.minusSeconds(1)).isEmpty());
        assertEquals(java.util.Set.of(room), assignments.roomsWithActiveImagingTechnician(java.util.List.of(room), start));
        assertTrue(assignments.roomsWithActiveImagingTechnician(java.util.List.of(room), end).isEmpty());
        assertTrue(assignments.isImagingTechnicianAssigned(actor, room, end.minusSeconds(1)));
        assertFalse(assignments.isImagingTechnicianAssigned(actor, room, end));
        assertFalse(assignments.isImagingTechnicianAssigned(actor, UUID.randomUUID(), start));
        jdbc.update("update staff_profiles set status='REMOVED' where id=?", profile);
        entityManager.clear();
        assertFalse(assignments.isImagingTechnicianAssigned(actor, room, start));
        assertTrue(assignments.roomsWithActiveImagingTechnician(java.util.List.of(room), start).isEmpty());
        jdbc.update("update staff_profiles set status='ACTIVE' where id=?", profile);
        jdbc.update("update accounts set status='DISABLED' where id=?", actor);
        entityManager.clear();
        assertFalse(assignments.isImagingTechnicianAssigned(actor, room, start));
        assertTrue(assignments.roomsWithActiveImagingTechnician(java.util.List.of(room), start).isEmpty());
    }
}
