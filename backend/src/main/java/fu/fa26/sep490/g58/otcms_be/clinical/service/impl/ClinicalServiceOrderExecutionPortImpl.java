package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Native SQL on {@code clinical_service_orders} for the imaging execution columns (see the port). Pending JPA
 * changes are flushed before each write so a managed ServiceOrderJpaEntity and these UPDATEs never interleave
 * out of order; {@code version} is never changed here (it belongs to that entity).
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ClinicalServiceOrderExecutionPortImpl implements ClinicalServiceOrderExecutionPort {
    private static final String COLUMNS = """
            select o.id, o.visit_id, o.patient_id, o.ordering_doctor_account_id, o.service_id, o.room_id,
                   o.imaging_type, o.body_area, o.clinical_reason, o.execution_status, o.cancellation_cause,
                   o.cancellation_reason, o.attempt_id, o.technician_account_id, o.first_use_type,
                   o.first_use_event_id, o.first_use_at, o.first_use_source_id, o.study_ref,
                   o.review_idempotency_key, o.review_fingerprint, o.cancel_idempotency_key, o.cancel_fingerprint,
                   o.transfer_idempotency_key, o.transfer_fingerprint, o.execution_version, o.execution_created_at,
                   o.execution_updated_at
            from clinical_service_orders o
            where o.execution_status is not null
            """;
    private static final String NEWEST_FIRST = " order by o.execution_created_at desc, o.id desc";

    private final EntityManager entityManager;

    public ClinicalServiceOrderExecutionPortImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // ---- reads ---------------------------------------------------------------------------------------------

    @Override
    public Optional<ImagingExecution> find(UUID orderId) {
        return single(query(COLUMNS + " and o.id = :id").setParameter("id", orderId));
    }

    @Override
    public Optional<ImagingExecution> findForUpdate(UUID orderId) {
        entityManager.flush();
        return single(query(COLUMNS + " and o.id = :id for update").setParameter("id", orderId));
    }

    @Override
    public Optional<ImagingExecution> findByStudyRef(String studyRef) {
        return single(query(COLUMNS + " and o.study_ref = :studyRef").setParameter("studyRef", studyRef));
    }

    @Override
    public List<ImagingExecution> findAllByOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return List.of();
        return list(query(COLUMNS + " and o.id in (:ids)" + NEWEST_FIRST).setParameter("ids", orderIds));
    }

    @Override
    public List<ImagingExecution> findAllByVisit(UUID visitId) {
        return list(query(COLUMNS + " and o.visit_id = :visitId" + NEWEST_FIRST).setParameter("visitId", visitId));
    }

    @Override
    public List<ImagingExecution> findAllByPatient(UUID patientId) {
        return list(query(COLUMNS + " and o.patient_id = :patientId" + NEWEST_FIRST)
                .setParameter("patientId", patientId));
    }

    @Override
    public Page<ImagingExecution> page(UUID visitId, UUID patientId, UUID orderingDoctorAccountId, UUID roomId,
            Collection<ExecutionStatus> statuses, boolean oldestFirst, Pageable pageable) {
        StringBuilder where = new StringBuilder();
        if (visitId != null) where.append(" and o.visit_id = :visitId");
        if (patientId != null) where.append(" and o.patient_id = :patientId");
        if (orderingDoctorAccountId != null) where.append(" and o.ordering_doctor_account_id = :doctorId");
        if (roomId != null) where.append(" and o.room_id = :roomId");
        if (statuses != null && !statuses.isEmpty()) where.append(" and o.execution_status in (:statuses)");
        String order = oldestFirst ? " order by o.execution_created_at asc, o.id asc" : NEWEST_FIRST;
        Query rows = query(COLUMNS + where + order);
        Query count = entityManager.createNativeQuery(
                "select count(*) from clinical_service_orders o where o.execution_status is not null" + where);
        for (Query q : List.of(rows, count)) {
            if (visitId != null) q.setParameter("visitId", visitId);
            if (patientId != null) q.setParameter("patientId", patientId);
            if (orderingDoctorAccountId != null) q.setParameter("doctorId", orderingDoctorAccountId);
            if (roomId != null) q.setParameter("roomId", roomId);
            if (statuses != null && !statuses.isEmpty()) {
                q.setParameter("statuses", statuses.stream().map(Enum::name).toList());
            }
        }
        if (pageable.isPaged()) {
            rows.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        }
        long total = ((Number) count.getSingleResult()).longValue();
        return new PageImpl<>(list(rows), pageable, total);
    }

    @Override
    public List<ImagingExecution> findPendingCreatedBefore(Instant createdBefore) {
        return list(query(COLUMNS + " and o.execution_status = 'PENDING' and o.execution_created_at < :before"
                + " order by o.execution_created_at asc").setParameter("before", Timestamp.from(createdBefore)));
    }

    // ---- guarded writes --------------------------------------------------------------------------------------

    @Override
    public int assignImagingRoom(UUID orderId, UUID visitId, UUID patientId, UUID serviceId, UUID roomId,
            String imagingType, UUID orderingDoctorAccountId) {
        return update("""
                update clinical_service_orders
                   set execution_status = 'PENDING', execution_created_at = clock_timestamp(),
                       execution_updated_at = clock_timestamp(), room_id = :roomId, imaging_type = :imagingType,
                       ordering_doctor_account_id = :doctorId
                 where id = :id and execution_status is null and service_type = 'IMAGING'
                   and order_status = 'ORDERED' and payment_status = 'PAID'
                   and visit_id = :visitId and patient_id = :patientId and service_id = :serviceId
                """, orderId, q -> q.setParameter("roomId", roomId).setParameter("imagingType", imagingType)
                .setParameter("doctorId", orderingDoctorAccountId).setParameter("visitId", visitId)
                .setParameter("patientId", patientId).setParameter("serviceId", serviceId));
    }

    @Override
    public int recordFirstUse(UUID orderId, long expectedVersion, UUID attemptId, UUID technicianAccountId,
            String firstUseType, UUID firstUseEventId, Instant firstUseAt, String firstUseSourceId) {
        return versioned("""
                update clinical_service_orders
                   set execution_status = 'IN_PROGRESS', attempt_id = :attemptId,
                       technician_account_id = :technicianId, first_use_type = :type,
                       first_use_event_id = :eventId, first_use_at = :at, first_use_source_id = :sourceId,
                       execution_version = execution_version + 1, execution_updated_at = clock_timestamp()
                 where id = :id and execution_version = :expectedVersion
                   and execution_status = 'PENDING' and attempt_id is null
                """, orderId, expectedVersion, q -> q.setParameter("attemptId", attemptId)
                .setParameter("technicianId", technicianAccountId).setParameter("type", firstUseType)
                .setParameter("eventId", firstUseEventId).setParameter("at", Timestamp.from(firstUseAt))
                .setParameter("sourceId", firstUseSourceId));
    }

    @Override
    public int linkStudy(UUID orderId, long expectedVersion, UUID attemptId, String studyRef) {
        return versioned("""
                update clinical_service_orders
                   set execution_status = 'RESULT_AVAILABLE', study_ref = :studyRef,
                       execution_version = execution_version + 1, execution_updated_at = clock_timestamp()
                 where id = :id and execution_version = :expectedVersion
                   and execution_status = 'IN_PROGRESS' and attempt_id = :attemptId and study_ref is null
                """, orderId, expectedVersion,
                q -> q.setParameter("attemptId", attemptId).setParameter("studyRef", studyRef));
    }

    @Override
    public int markReviewed(UUID orderId, long expectedVersion, String idempotencyKey, String fingerprint) {
        return versioned("""
                update clinical_service_orders
                   set execution_status = 'REVIEWED', review_idempotency_key = :key, review_fingerprint = :fp,
                       execution_version = execution_version + 1, execution_updated_at = clock_timestamp()
                 where id = :id and execution_version = :expectedVersion
                   and execution_status = 'RESULT_AVAILABLE'
                """, orderId, expectedVersion,
                q -> q.setParameter("key", idempotencyKey).setParameter("fp", fingerprint));
    }

    @Override
    public int cancel(UUID orderId, long expectedVersion, String cause, String reason, String idempotencyKey,
            String fingerprint) {
        return versioned("""
                update clinical_service_orders
                   set execution_status = 'CANCELLED', cancellation_cause = :cause, cancellation_reason = :reason,
                       cancel_idempotency_key = :key, cancel_fingerprint = :fp,
                       execution_version = execution_version + 1, execution_updated_at = clock_timestamp()
                 where id = :id and execution_version = :expectedVersion and execution_status = 'PENDING'
                """, orderId, expectedVersion, q -> q.setParameter("cause", cause).setParameter("reason", reason)
                .setParameter("key", idempotencyKey).setParameter("fp", fingerprint));
    }

    @Override
    public int transferRoom(UUID orderId, long expectedVersion, UUID roomId, String idempotencyKey,
            String fingerprint) {
        return versioned("""
                update clinical_service_orders
                   set room_id = :roomId, transfer_idempotency_key = :key, transfer_fingerprint = :fp,
                       execution_version = execution_version + 1, execution_updated_at = clock_timestamp()
                 where id = :id and execution_version = :expectedVersion and execution_status = 'PENDING'
                """, orderId, expectedVersion, q -> q.setParameter("roomId", roomId)
                .setParameter("key", idempotencyKey).setParameter("fp", fingerprint));
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private int versioned(String sql, UUID orderId, long expectedVersion,
            java.util.function.Consumer<Query> parameters) {
        return update(sql, orderId, q -> parameters.accept(q.setParameter("expectedVersion", expectedVersion)));
    }

    private int update(String sql, UUID orderId, java.util.function.Consumer<Query> parameters) {
        entityManager.flush();
        Query query = entityManager.createNativeQuery(sql).setParameter("id", orderId);
        parameters.accept(query);
        return query.executeUpdate();
    }

    private Query query(String sql) {
        return entityManager.createNativeQuery(sql, Tuple.class);
    }

    private static Optional<ImagingExecution> single(Query query) {
        List<ImagingExecution> rows = list(query);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @SuppressWarnings("unchecked")
    private static List<ImagingExecution> list(Query query) {
        List<ImagingExecution> result = new ArrayList<>();
        for (Tuple t : (List<Tuple>) query.getResultList()) {
            result.add(new ImagingExecution(uuid(t, "id"), uuid(t, "visit_id"), uuid(t, "patient_id"),
                    uuid(t, "ordering_doctor_account_id"), uuid(t, "service_id"), uuid(t, "room_id"),
                    text(t, "imaging_type"), text(t, "body_area"), text(t, "clinical_reason"),
                    ExecutionStatus.valueOf(text(t, "execution_status")), text(t, "cancellation_cause"),
                    text(t, "cancellation_reason"), uuid(t, "attempt_id"), uuid(t, "technician_account_id"),
                    text(t, "first_use_type"), uuid(t, "first_use_event_id"), instant(t, "first_use_at"),
                    text(t, "first_use_source_id"), text(t, "study_ref"),
                    text(t, "review_idempotency_key"), trimmed(t, "review_fingerprint"),
                    text(t, "cancel_idempotency_key"), trimmed(t, "cancel_fingerprint"),
                    text(t, "transfer_idempotency_key"), trimmed(t, "transfer_fingerprint"),
                    ((Number) t.get("execution_version")).longValue(),
                    instant(t, "execution_created_at"), instant(t, "execution_updated_at")));
        }
        return result;
    }

    private static UUID uuid(Tuple t, String column) {
        Object value = t.get(column);
        return value == null ? null : value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static String text(Tuple t, String column) {
        Object value = t.get(column);
        return value == null ? null : value.toString();
    }

    /** Fingerprints are char(64); trim defensively so comparisons with Java hex strings stay exact. */
    private static String trimmed(Tuple t, String column) {
        String value = text(t, column);
        return value == null ? null : value.trim();
    }

    private static Instant instant(Tuple t, String column) {
        Object value = t.get(column);
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof Timestamp ts) return ts.toInstant();
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(value.toString());
    }
}
