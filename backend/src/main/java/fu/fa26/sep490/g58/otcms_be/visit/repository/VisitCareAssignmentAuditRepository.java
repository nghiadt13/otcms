package fu.fa26.sep490.g58.otcms_be.visit.repository;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class VisitCareAssignmentAuditRepository {
    private final EntityManager entityManager;

    public VisitCareAssignmentAuditRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void append(UUID visitId, UUID assignmentId, UUID actorAccountId, String action, String stepCode,
            Timestamp oldExpectedEnd, Timestamp newExpectedEnd, Timestamp occurredAt, String correlationId) {
        entityManager.createNativeQuery("""
                insert into visit_assignment_audits
                    (id, visit_id, assignment_id, actor_account_id, action, step_code,
                     old_expected_end, new_expected_end, occurred_at, correlation_id)
                values (:id, :visitId, :assignmentId, :actorId, :action, :stepCode,
                        :oldEnd, :newEnd, :occurredAt, :correlationId)
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("visitId", visitId)
                .setParameter("assignmentId", assignmentId)
                .setParameter("actorId", actorAccountId)
                .setParameter("action", action)
                .setParameter("stepCode", stepCode)
                .setParameter("oldEnd", oldExpectedEnd)
                .setParameter("newEnd", newExpectedEnd)
                .setParameter("occurredAt", occurredAt)
                .setParameter("correlationId", correlationId)
                .executeUpdate();
    }

    public void appendConflict(UUID visitId, UUID assignmentId, UUID actorAccountId, String correlationId,
            String attemptedStepCode, Timestamp occurredAt) {
        entityManager.createNativeQuery("""
                insert into visit_assignment_audits
                    (id, visit_id, assignment_id, actor_account_id, action, step_code,
                     old_expected_end, new_expected_end, occurred_at, correlation_id)
                values (:id, :visitId, :assignmentId, :actorId, 'CAPACITY_CONFLICT', :stepCode,
                        null, null, :occurredAt, :correlationId)
                on conflict do nothing
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("visitId", visitId)
                .setParameter("assignmentId", assignmentId)
                .setParameter("actorId", actorAccountId)
                .setParameter("stepCode", attemptedStepCode)
                .setParameter("occurredAt", occurredAt)
                .setParameter("correlationId", correlationId)
                .executeUpdate();
    }
}
