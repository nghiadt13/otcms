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

    public record Assignment(UUID roomId, UUID accountId) { }

    public java.util.List<Assignment> findCurrentAssignments(java.util.Collection<UUID> roomIds, Instant at) {
        if (roomIds.isEmpty()) return java.util.List.of();
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query("""
                select distinct s.room_id, p.account_id
                from work_shifts s join staff_profiles p on p.id = s.staff_profile_id join accounts a on a.id = p.account_id and a.account_type <> 'DOCTOR'
                where p.status = 'ACTIVE' and s.room_id in (:roomIds)
                  and s.starts_at <= :at and s.ends_at > :at
                """, java.util.Map.of("roomIds", roomIds, "at", Timestamp.from(at)),
                (row, index) -> new Assignment(row.getObject("room_id", UUID.class), row.getObject("account_id", UUID.class)));
    }

    public boolean hasActiveAssignment(UUID accountId, UUID roomId, Instant at) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from work_shifts s join staff_profiles p on p.id = s.staff_profile_id join accounts a on a.id = p.account_id and a.account_type <> 'DOCTOR'
                    where p.account_id = ? and p.status = 'ACTIVE' and s.room_id = ?
                        and s.starts_at <= ? and s.ends_at > ?
                )
                """, Boolean.class, accountId, roomId, Timestamp.from(at), Timestamp.from(at)));
    }
}
