package fu.fa26.sep490.g58.otcms_be.visit.repository;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentArrivalRepository {
    private final EntityManager entityManager;
    public AppointmentArrivalRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public int claimIfUnrecorded(UUID appointment, UUID patient, UUID actor, String source, String reason, Timestamp arrivedAt) {
        return entityManager.createNativeQuery("""
                update appointments set arrived_at = :arrivedAt, arrival_source = :source,
                    arrival_reason = cast(:reason as varchar), arrival_recorded_by = :actor,
                    arrival_recorded_at = clock_timestamp()
                where id = :appointment and patient_profile_id = :patient and arrived_at is null
                """).setParameter("appointment", appointment).setParameter("patient", patient)
                .setParameter("actor", actor).setParameter("source", source).setParameter("reason", reason)
                .setParameter("arrivedAt", arrivedAt).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> findArrival(UUID appointment) {
        return entityManager.createNativeQuery("""
                select id, patient_profile_id, arrival_recorded_by, arrival_source, arrival_reason,
                    arrived_at, arrival_recorded_at from appointments
                where id = :appointment and arrived_at is not null
                """).setParameter("appointment", appointment).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> findArrivals(Timestamp from, Timestamp to, int offset, int limit) {
        return entityManager.createNativeQuery("""
                select id, patient_profile_id, arrival_recorded_by, arrival_source, arrival_reason,
                    arrived_at, arrival_recorded_at from appointments
                where arrived_at >= :from and arrived_at < :to order by arrived_at, id
                """).setParameter("from", from).setParameter("to", to)
                .setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> findRecordedArrivalEvidence(UUID appointmentId, UUID patientProfileId,
            Timestamp windowStart, Timestamp windowEnd) {
        return entityManager.createNativeQuery("""
                select id, patient_profile_id, arrived_at, arrival_recorded_by from appointments
                where id = :appointmentId and patient_profile_id = :patientProfileId
                    and arrived_at >= :windowStart and arrived_at <= :windowEnd
                """).setParameter("appointmentId", appointmentId).setParameter("patientProfileId", patientProfileId)
                .setParameter("windowStart", windowStart).setParameter("windowEnd", windowEnd).getResultList();
    }
}
