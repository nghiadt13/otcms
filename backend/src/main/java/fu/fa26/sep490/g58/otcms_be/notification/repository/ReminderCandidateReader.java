package fu.fa26.sep490.g58.otcms_be.notification.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only lookup of the appointments that need an NF-09 reminder. Reads {@code appointments} and
 * {@code patient_profiles} directly (cross-feature read, flagged for owner review) because the appointment package
 * exposes no "due for reminder" query.
 */
@Repository
public class ReminderCandidateReader {
    /** One appointment that may need a reminder; {@code followUp} is true for a doctor-recommended follow-up (BR-63). */
    public record Candidate(UUID appointmentId, UUID patientProfileId, String patientName, boolean followUp, Instant startTime) { }

    private final NamedParameterJdbcTemplate jdbc;

    public ReminderCandidateReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Confirmed appointments that start after {@code from} and no later than {@code until}. */
    public List<Candidate> upcoming(Instant from, Instant until) {
        return jdbc.query("""
                SELECT a.id, a.patient_profile_id, p.full_name, a.start_time,
                       a.booking_source = 'DOCTOR_FOLLOW_UP' AS follow_up
                FROM appointments a JOIN patient_profiles p ON p.id = a.patient_profile_id
                WHERE a.status = 'CONFIRMED' AND a.start_time > :from AND a.start_time <= :until
                ORDER BY a.start_time, a.id
                """, new MapSqlParameterSource("from", Timestamp.from(from)).addValue("until", Timestamp.from(until)),
                (rs, row) -> new Candidate(rs.getObject("id", UUID.class), rs.getObject("patient_profile_id", UUID.class),
                        rs.getString("full_name"), rs.getBoolean("follow_up"), rs.getTimestamp("start_time").toInstant()));
    }

    /** Follow-ups whose time passed without the patient arriving (system marked them NO_SHOW), not older than {@code since}. */
    public List<Candidate> overdueFollowUps(Instant now, Instant since) {
        return jdbc.query("""
                SELECT a.id, a.patient_profile_id, p.full_name, a.start_time
                FROM appointments a JOIN patient_profiles p ON p.id = a.patient_profile_id
                WHERE a.booking_source = 'DOCTOR_FOLLOW_UP' AND a.status = 'NO_SHOW'
                  AND a.start_time < :now AND a.start_time >= :since
                ORDER BY a.start_time, a.id
                """, new MapSqlParameterSource("now", Timestamp.from(now)).addValue("since", Timestamp.from(since)),
                (rs, row) -> new Candidate(rs.getObject("id", UUID.class), rs.getObject("patient_profile_id", UUID.class),
                        rs.getString("full_name"), true, rs.getTimestamp("start_time").toInstant()));
    }
}
