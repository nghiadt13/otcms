package fu.fa26.sep490.g58.otcms_be.staff;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffRoomAssignmentQueryService;
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
    @Autowired StaffRoomAssignmentQueryService assignments;
    @Test void shiftBoundariesRoomIdentityProfileAndAccountStatusAreAuthoritative() {
        UUID actor = UUID.randomUUID(), profile = UUID.randomUUID(), room = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-05T03:00:00Z"), end = start.plusSeconds(3600);
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'IMAGING_TECHNICIAN','ACTIVE',now(),now())", actor, actor + "@assignment.test");
        jdbc.update("insert into staff_profiles(id,account_id,display_name,work_contact) values (?,?,'Shift technician','')", profile, actor);
        jdbc.update("insert into rooms(id,room_code,display_label,room_type) values (?,?,'Assignment room','IMAGING')", room, "ASSIGN-" + room);
        jdbc.update("insert into staff_work_shifts(staff_profile_id,room_id,starts_at,ends_at) values (?,?,?,?)", profile, room, Timestamp.from(start), Timestamp.from(end));
        assertFalse(assignments.isImagingTechnicianAssigned(actor, room, start.minusSeconds(1)));
        assertTrue(assignments.isImagingTechnicianAssigned(actor, room, start));
        assertTrue(assignments.isImagingTechnicianAssigned(actor, room, end.minusSeconds(1)));
        assertFalse(assignments.isImagingTechnicianAssigned(actor, room, end));
        assertFalse(assignments.isImagingTechnicianAssigned(actor, UUID.randomUUID(), start));
        jdbc.update("update staff_profiles set status='REMOVED' where id=?", profile);
        assertFalse(assignments.isImagingTechnicianAssigned(actor, room, start));
        jdbc.update("update staff_profiles set status='ACTIVE' where id=?", profile);
        jdbc.update("update accounts set status='DISABLED' where id=?", actor);
        assertFalse(assignments.isImagingTechnicianAssigned(actor, room, start));
    }
}
