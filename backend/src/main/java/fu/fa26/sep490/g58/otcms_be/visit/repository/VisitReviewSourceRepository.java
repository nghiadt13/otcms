package fu.fa26.sep490.g58.otcms_be.visit.repository;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class VisitReviewSourceRepository {
    private final EntityManager entityManager;
    public VisitReviewSourceRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    public record Row(UUID id, UUID patientProfileId, String status, Instant arrivedAt,
            UUID appointmentId, UUID doctorId) { }
    private static final String SELECT = """
            select v.id, v.patient_profile_id, v.status, v.arrived_at,
                   v.originating_appointment_id,
                   (select c.doctor_profile_id from visit_assignments c where c.visit_id = v.id
                       order by c.starts_at desc, c.id desc limit 1)
            from visits v
            """;
    @SuppressWarnings("unchecked")
    public Optional<Row> find(UUID id) {
        List<Object[]> rows = entityManager.createNativeQuery(SELECT + " where v.id = :id")
                .setParameter("id", id).getResultList();
        return rows.stream().map(this::map).findFirst();
    }
    @SuppressWarnings("unchecked")
    public List<Row> closedVisits(UUID profileId) {
        List<Object[]> rows = entityManager.createNativeQuery(SELECT
                + " where v.patient_profile_id = :profile and v.status = 'CLOSED' order by v.arrived_at desc, v.id")
                .setParameter("profile", profileId).getResultList();
        return rows.stream().map(this::map).toList();
    }
    private Row map(Object[] row) {
        Instant arrived = row[3] instanceof Instant instant ? instant
                : row[3] instanceof OffsetDateTime offset ? offset.toInstant() : ((Timestamp) row[3]).toInstant();
        return new Row((UUID) row[0], (UUID) row[1], (String) row[2], arrived, (UUID) row[4], (UUID) row[5]);
    }
}
