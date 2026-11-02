package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.ScheduledCarePersistenceData;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Persistence operations for atomic Visit workflow changes. */
@Repository
public class VisitWorkflowRepository {
    private final EntityManager entityManager;

    public VisitWorkflowRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    public static class FollowUpEvidenceRow {
        private final UUID patientProfileId, originatingAppointmentId;
        private final boolean assigned, examinationCompleted, followUpIndicated;
        public FollowUpEvidenceRow(UUID patientProfileId, UUID originatingAppointmentId, boolean assigned,
                boolean examinationCompleted, boolean followUpIndicated) {
            this.patientProfileId = patientProfileId; this.originatingAppointmentId = originatingAppointmentId;
            this.assigned = assigned; this.examinationCompleted = examinationCompleted; this.followUpIndicated = followUpIndicated;
        }
        public UUID patientProfileId() { return patientProfileId; }
        public UUID originatingAppointmentId() { return originatingAppointmentId; }
        public boolean assigned() { return assigned; }
        public boolean examinationCompleted() { return examinationCompleted; }
        public boolean followUpIndicated() { return followUpIndicated; }
    }
    public static class CompletedCareRow {
        private final UUID assignmentId; private final String stepCode; private final Timestamp expectedEnd;
        public CompletedCareRow(UUID assignmentId, String stepCode, Timestamp expectedEnd) {
            this.assignmentId = assignmentId; this.stepCode = stepCode; this.expectedEnd = expectedEnd;
        }
        public UUID assignmentId() { return assignmentId; }
        public String stepCode() { return stepCode; }
        public Timestamp expectedEnd() { return expectedEnd; }
    }

    public FollowUpEvidenceRow findFollowUpEvidence(UUID visitId, UUID doctorId) {
        List<?> rows = entityManager.createNativeQuery("""
                select v.patient_profile_id, v.originating_appointment_id,
                    exists (select 1 from visit_assignments c where c.visit_id = v.id and c.doctor_profile_id = :doctorId and c.status <> 'RELEASED'),
                    exists (select 1 from visit_examinations e where e.visit_id = v.id and e.doctor_profile_id = :doctorId and e.completed),
                    exists (select 1 from visit_examinations e where e.visit_id = v.id and e.doctor_profile_id = :doctorId and e.completed and e.follow_up_indicated)
                from visits v where v.id = :visitId
                """).setParameter("doctorId", doctorId).setParameter("visitId", visitId).getResultList();
        if (rows.isEmpty()) return null;
        Object[] row = (Object[]) rows.getFirst();
        return new FollowUpEvidenceRow((UUID) row[0], (UUID) row[1], (Boolean) row[2], (Boolean) row[3], (Boolean) row[4]);
    }

    public boolean lockScheduledVisit(UUID visitId, UUID appointmentId, UUID patientId) {
        return entityManager.createNativeQuery("""
                select id from visits where id = :visitId and originating_appointment_id = :appointmentId
                  and patient_profile_id = :patientId and source = 'SCHEDULED' and status = 'OPEN' for update
                """).setParameter("visitId", visitId).setParameter("appointmentId", appointmentId)
                .setParameter("patientId", patientId).getResultStream().findFirst().isPresent();
    }

    public boolean hasActiveCare(UUID visitId) {
        Number count = (Number) entityManager.createNativeQuery("select count(*) from visit_assignments where visit_id = :visitId and status in ('RESERVED', 'IN_PROGRESS')")
                .setParameter("visitId", visitId).getSingleResult();
        return count.intValue() != 0;
    }

    public boolean hasDoctorCareConflict(UUID doctorId, Instant now, Instant scheduledEnd) {
        Number count = (Number) entityManager.createNativeQuery("""
                select count(*) from visit_assignments where doctor_profile_id = :doctorId
                  and status in ('RESERVED', 'IN_PROGRESS') and starts_at < :scheduledEnd and ends_at > :now
                """).setParameter("doctorId", doctorId).setParameter("scheduledEnd", Timestamp.from(scheduledEnd))
                .setParameter("now", Timestamp.from(now)).getSingleResult();
        return count.intValue() != 0;
    }

    public void insertScheduledCare(UUID assignmentId, ScheduledCarePersistenceData command, Instant now) {
        entityManager.createNativeQuery("""
                insert into visit_assignments (id, visit_id, doctor_profile_id, starts_at, ends_at, status,
                    assigned_at, step_code, expected_duration_minutes)
                values (:id, :visitId, :doctorId, :now, :scheduledEnd, 'IN_PROGRESS', :now, 'SCHEDULED_APPOINTMENT', :duration)
                """).setParameter("id", assignmentId).setParameter("visitId", command.visitId())
                .setParameter("doctorId", command.doctorProfileId()).setParameter("now", Timestamp.from(now))
                .setParameter("scheduledEnd", Timestamp.from(command.scheduledEnd()))
                .setParameter("duration", command.expectedDurationMinutes()).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public CompletedCareRow completeScheduledCare(ScheduledCarePersistenceData command) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                update visit_assignments c set status = 'COMPLETED', actual_ended_at = :actualEnd
                where c.visit_id = :visitId and c.doctor_profile_id = :doctorId and c.status = 'IN_PROGRESS'
                  and exists (select 1 from visits v where v.id = c.visit_id and v.originating_appointment_id = :appointmentId
                    and v.patient_profile_id = :patientId and v.status = 'OPEN') returning c.id, c.step_code, c.ends_at
                """).setParameter("visitId", command.visitId()).setParameter("doctorId", command.doctorProfileId())
                .setParameter("appointmentId", command.appointmentId()).setParameter("patientId", command.patientProfileId())
                .setParameter("actualEnd", Timestamp.from(command.occurredAt())).getResultList();
        if (rows.size() != 1) return null;
        Object[] row = rows.getFirst();
        return new CompletedCareRow((UUID) row[0], (String) row[1], toTimestamp(row[2]));
    }

    public boolean closeCancellableVisit(ScheduledCarePersistenceData command) {
        Number changed = (Number) entityManager.createNativeQuery("""
                update visits v set status = 'CLOSED', updated_at = :now
                where v.id = :visitId and v.originating_appointment_id = :appointmentId and v.patient_profile_id = :patientId
                  and v.source = 'SCHEDULED' and v.status = 'OPEN'
                  and not exists (select 1 from visit_examinations e where e.visit_id = v.id)
                  and not exists (select 1 from visit_assignments c where c.visit_id = v.id and c.status in ('IN_PROGRESS', 'COMPLETED'))
                """).setParameter("now", Timestamp.from(command.occurredAt())).setParameter("visitId", command.visitId())
                .setParameter("appointmentId", command.appointmentId()).setParameter("patientId", command.patientProfileId()).executeUpdate();
        return changed.intValue() == 1;
    }

    public void releaseReservedCare(UUID visitId, Instant now) {
        entityManager.createNativeQuery("update visit_assignments set status = 'RELEASED', released_at = :now where visit_id = :visitId and status = 'RESERVED'")
                .setParameter("now", Timestamp.from(now)).setParameter("visitId", visitId).executeUpdate();
    }

    public UUID findActiveAssignment(UUID visitId, UUID doctorId) {
        return (UUID) entityManager.createNativeQuery("select id from visit_assignments where visit_id = :visitId and doctor_profile_id = :doctorId and status = 'IN_PROGRESS' order by assigned_at desc, id desc limit 1")
                .setParameter("visitId", visitId).setParameter("doctorId", doctorId).getSingleResult();
    }

    public void appendCareAudit(UUID visitId, UUID assignmentId, UUID actorId, String action, String stepCode,
            Timestamp oldEnd, Timestamp newEnd, Instant occurredAt, String correlationId) {
        entityManager.createNativeQuery("""
                insert into visit_assignment_audits (id, visit_id, assignment_id, actor_account_id, action, step_code,
                    old_expected_end, new_expected_end, occurred_at, correlation_id)
                values (:id, :visitId, :assignmentId, :actorId, :action, :stepCode, :oldEnd, :newEnd, :occurredAt, :correlationId)
                """).setParameter("id", UUID.randomUUID()).setParameter("visitId", visitId).setParameter("assignmentId", assignmentId)
                .setParameter("actorId", actorId).setParameter("action", action).setParameter("stepCode", stepCode)
                .setParameter("oldEnd", oldEnd).setParameter("newEnd", newEnd).setParameter("occurredAt", Timestamp.from(occurredAt))
                .setParameter("correlationId", correlationId).executeUpdate();
    }

    public void appendCapacityConflict(UUID visitId, UUID actorId, Instant occurredAt, String correlationId) {
        entityManager.createNativeQuery("""
                insert into visit_assignment_audits (id, visit_id, assignment_id, actor_account_id, action, step_code,
                    old_expected_end, new_expected_end, occurred_at, correlation_id)
                values (:id, :visitId, null, :actorId, 'CAPACITY_CONFLICT', 'SCHEDULED_APPOINTMENT', null, null, :occurredAt, :correlationId)
                on conflict do nothing
                """).setParameter("id", UUID.randomUUID()).setParameter("visitId", visitId).setParameter("actorId", actorId)
                .setParameter("occurredAt", Timestamp.from(occurredAt)).setParameter("correlationId", correlationId).executeUpdate();
    }

    public boolean hasVisitFromAppointment(UUID appointmentId) {
        Number count = (Number) entityManager.createNativeQuery("select count(*) from visits where originating_appointment_id = :appointmentId")
                .setParameter("appointmentId", appointmentId).getSingleResult();
        return count.intValue() != 0;
    }

    public List<Object[]> findRecordedArrival(UUID appointmentId) {
        return entityManager.createNativeQuery("select patient_profile_id, arrived_at from appointment_arrivals where appointment_id = :appointmentId")
                .setParameter("appointmentId", appointmentId).getResultList();
    }

    public void insertCheckInVisit(UUID visitId, CheckInData data) {
        entityManager.createNativeQuery("""
                insert into visits (id, patient_profile_id, source, originating_appointment_id, prior_appointment_id,
                    arrived_at, checked_in_at, created_by_account_id, status)
                values (:visitId, :patientId, 'SCHEDULED', :appointmentId, null, :arrivedAt, :checkedInAt, :actorId, 'OPEN')
                """).setParameter("visitId", visitId).setParameter("patientId", data.patientId()).setParameter("appointmentId", data.appointmentId())
                .setParameter("arrivedAt", Timestamp.from(data.arrivedAt())).setParameter("checkedInAt", Timestamp.from(data.checkedInAt()))
                .setParameter("actorId", data.actorId()).executeUpdate();
        entityManager.createNativeQuery("""
                insert into visit_check_ins (visit_id, receptionist_account_id, reception_type, arrived_at, checked_in_at, prior_appointment_id)
                values (:visitId, :actorId, 'APPOINTMENT_CHECK_IN', :arrivedAt, :checkedInAt, null)
                """).setParameter("visitId", visitId).setParameter("actorId", data.actorId())
                .setParameter("arrivedAt", Timestamp.from(data.arrivedAt())).setParameter("checkedInAt", Timestamp.from(data.checkedInAt())).executeUpdate();
    }
    public static class CheckInData {
        private final UUID appointmentId, patientId, actorId; private final Instant arrivedAt, checkedInAt;
        public CheckInData(UUID appointmentId, UUID patientId, UUID actorId, Instant arrivedAt, Instant checkedInAt) {
            this.appointmentId = appointmentId; this.patientId = patientId; this.actorId = actorId;
            this.arrivedAt = arrivedAt; this.checkedInAt = checkedInAt;
        }
        public UUID appointmentId() { return appointmentId; }
        public UUID patientId() { return patientId; }
        public UUID actorId() { return actorId; }
        public Instant arrivedAt() { return arrivedAt; }
        public Instant checkedInAt() { return checkedInAt; }
    }

    public boolean hasConsistentCheckInEvidence(UUID appointmentId, UUID patientId, UUID visitId) {
        Boolean consistent = (Boolean) entityManager.createNativeQuery("""
                select exists (select 1 from visits v join visit_check_ins r on r.visit_id = v.id
                where v.id = :visitId and v.originating_appointment_id = :appointmentId and v.patient_profile_id = :patientId
                and v.source = 'SCHEDULED' and r.reception_type = 'APPOINTMENT_CHECK_IN' and r.prior_appointment_id is null
                and r.receptionist_account_id is not null and r.checked_in_at = v.checked_in_at and r.arrived_at = v.arrived_at)
                """).setParameter("visitId", visitId).setParameter("appointmentId", appointmentId).setParameter("patientId", patientId).getSingleResult();
        return Boolean.TRUE.equals(consistent);
    }

    private static Timestamp toTimestamp(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp;
        if (value instanceof Instant instant) return Timestamp.from(instant);
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return Timestamp.from(offsetDateTime.toInstant());
        throw new IllegalStateException("Unexpected care timestamp value");
    }
}
