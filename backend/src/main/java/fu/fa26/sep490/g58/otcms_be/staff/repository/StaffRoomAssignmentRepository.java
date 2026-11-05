package fu.fa26.sep490.g58.otcms_be.staff.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StaffRoomAssignmentRepository {
    private final JdbcTemplate jdbc;
    public StaffRoomAssignmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean hasActiveAssignment(UUID accountId, UUID roomId, Instant at) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from staff_work_shifts s join staff_profiles p on p.id = s.staff_profile_id
                    where p.account_id = ? and p.status = 'ACTIVE' and s.room_id = ?
                        and s.starts_at <= ? and s.ends_at > ?
                )
                """, Boolean.class, accountId, roomId, Timestamp.from(at), Timestamp.from(at)));
    }
}
