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

    /** Inserts the arrival group, or fills it when only a check-in group exists; 0 when already recorded or not the patient's. */
    public int claimIfUnrecorded(UUID appointment, UUID patient, UUID actor, String source, String reason, Timestamp arrivedAt) {
        return entityManager.createNativeQuery("""
                insert into appointment_arrivals (appointment_id, arrived_at, arrival_source, arrival_reason,
                    arrival_recorded_by, arrival_recorded_at)
                select a.id, :arrivedAt, :source, cast(:reason as varchar), :actor, clock_timestamp()
                from appointments a where a.id = :appointment and a.patient_profile_id = :patient
                on conflict (appointment_id) do update set arrived_at = excluded.arrived_at,
                    arrival_source = excluded.arrival_source, arrival_reason = excluded.arrival_reason,
                    arrival_recorded_by = excluded.arrival_recorded_by, arrival_recorded_at = excluded.arrival_recorded_at
                where appointment_arrivals.arrived_at is null
                """).setParameter("appointment", appointment).setParameter("patient", patient)
                .setParameter("actor", actor).setParameter("source", source).setParameter("reason", reason)
                .setParameter("arrivedAt", arrivedAt).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> findArrival(UUID appointment) {
        return entityManager.createNativeQuery("""
                select a.id, a.patient_profile_id, r.arrival_recorded_by, r.arrival_source, r.arrival_reason,
                    r.arrived_at, r.arrival_recorded_at from appointments a
                join appointment_arrivals r on r.appointment_id = a.id
                where a.id = :appointment and r.arrived_at is not null
                """).setParameter("appointment", appointment).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> findArrivals(Timestamp from, Timestamp to, int offset, int limit) {
        return entityManager.createNativeQuery("""
                select a.id, a.patient_profile_id, r.arrival_recorded_by, r.arrival_source, r.arrival_reason,
                    r.arrived_at, r.arrival_recorded_at from appointments a
                join appointment_arrivals r on r.appointment_id = a.id
                where r.arrived_at >= :from and r.arrived_at < :to order by r.arrived_at, a.id
                """).setParameter("from", from).setParameter("to", to)
                .setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> findRecordedArrivalEvidence(UUID appointmentId, UUID patientProfileId,
            Timestamp windowStart, Timestamp windowEnd) {
        return entityManager.createNativeQuery("""
                select a.id, a.patient_profile_id, r.arrived_at, r.arrival_recorded_by from appointments a
                join appointment_arrivals r on r.appointment_id = a.id
                where a.id = :appointmentId and a.patient_profile_id = :patientProfileId
                    and r.arrived_at >= :windowStart and r.arrived_at <= :windowEnd
                """).setParameter("appointmentId", appointmentId).setParameter("patientProfileId", patientProfileId)
                .setParameter("windowStart", windowStart).setParameter("windowEnd", windowEnd).getResultList();
    }
}
