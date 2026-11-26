package fu.fa26.sep490.g58.otcms_be.clinical.repository;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalImagingExecutionRow;

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
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Native SQL on {@code imaging_executions} (reads join {@code service_orders}; see the port). Pending JPA
 * changes are flushed before each write so a managed ServiceOrderJpaEntity and these UPDATEs never interleave out of
 * order. {@code version} here is the execution's lock, changed only by the guarded writes below; the order's JPA
 * version is never changed here.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ClinicalServiceOrderExecutionRepository {
    private static final String COLUMNS = """
            select o.id, o.visit_id, o.patient_id, e.ordering_doctor_account_id, o.service_id, o.room_id,
                   p.dicom_patient_id, p.dicom_patient_issuer, o.dicom_accession_number,
                   o.dicom_accession_local_namespace,
                   e.imaging_type, o.body_area, o.clinical_reason, e.execution_status, e.cancellation_cause,
                   e.cancellation_reason, e.attempt_id, e.technician_account_id, e.first_use_type,
                   e.first_use_event_id, e.first_use_at, e.first_use_source_id, e.study_ref,
                   e.version as execution_version,
                   e.created_at as created_at, e.updated_at as updated_at
            from service_orders o
            join imaging_executions e on e.service_order_id = o.id
            join patient_profiles p on p.id = o.patient_id
            where e.service_order_id is not null
            """;
    private static final String NEWEST_FIRST = " order by e.created_at desc, o.id desc";

    private final EntityManager entityManager;

    public ClinicalServiceOrderExecutionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // ---- reads ---------------------------------------------------------------------------------------------

    public Optional<ClinicalImagingExecutionRow> find(UUID orderId) {
        return single(query(COLUMNS + " and o.id = :id").setParameter("id", orderId));
    }

    public Optional<ClinicalImagingExecutionRow> findForUpdate(UUID orderId) {
        entityManager.flush();
        return single(query(COLUMNS + " and o.id = :id for update of o, e").setParameter("id", orderId));
    }

    public Optional<ClinicalImagingExecutionRow> findByStudyRef(String studyRef) {
        return single(query(COLUMNS + " and e.study_ref = :studyRef").setParameter("studyRef", studyRef));
    }

    public Optional<ClinicalImagingExecutionRow> findByFirstUseEventId(UUID firstUseEventId) {
        return single(query(COLUMNS + " and e.first_use_event_id = :eventId")
                .setParameter("eventId", firstUseEventId));
    }

    public List<ClinicalImagingExecutionRow> findAllByOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return List.of();
        return list(query(COLUMNS + " and o.id in (:ids)" + NEWEST_FIRST).setParameter("ids", orderIds));
    }

    public List<ClinicalImagingExecutionRow> findAllByVisit(UUID visitId) {
        return list(query(COLUMNS + " and o.visit_id = :visitId" + NEWEST_FIRST).setParameter("visitId", visitId));
    }

    public List<ClinicalImagingExecutionRow> findAllByPatient(UUID patientId) {
        return list(query(COLUMNS + " and o.patient_id = :patientId" + NEWEST_FIRST)
                .setParameter("patientId", patientId));
    }

    public Page<ClinicalImagingExecutionRow> page(UUID visitId, UUID patientId, UUID orderingDoctorAccountId, UUID roomId,
            Collection<String> statuses, boolean oldestFirst, Pageable pageable) {
        StringBuilder where = new StringBuilder();
        if (visitId != null) where.append(" and o.visit_id = :visitId");
        if (patientId != null) where.append(" and o.patient_id = :patientId");
        if (orderingDoctorAccountId != null) where.append(" and e.ordering_doctor_account_id = :doctorId");
        if (roomId != null) where.append(" and o.room_id = :roomId");
        if (statuses != null && !statuses.isEmpty()) where.append(" and e.execution_status in (:statuses)");
        String order = oldestFirst ? " order by e.created_at asc, o.id asc" : NEWEST_FIRST;
        Query rows = query(COLUMNS + where + order);
        Query count = entityManager.createNativeQuery(
                "select count(*) from service_orders o join imaging_executions e on e.service_order_id = o.id"
                        + " where e.service_order_id is not null" + where);
        for (Query q : List.of(rows, count)) {
            if (visitId != null) q.setParameter("visitId", visitId);
            if (patientId != null) q.setParameter("patientId", patientId);
            if (orderingDoctorAccountId != null) q.setParameter("doctorId", orderingDoctorAccountId);
            if (roomId != null) q.setParameter("roomId", roomId);
            if (statuses != null && !statuses.isEmpty()) {
                q.setParameter("statuses", statuses);
            }
        }
        if (pageable.isPaged()) {
            rows.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        }
        long total = ((Number) count.getSingleResult()).longValue();
        return new PageImpl<>(list(rows), pageable, total);
    }

    public List<ClinicalImagingExecutionRow> findPendingCreatedBefore(Instant createdBefore) {
        return list(query(COLUMNS + " and e.execution_status = 'PENDING' and e.created_at < :before"
                + " order by e.created_at asc").setParameter("before", Timestamp.from(createdBefore)));
    }

    // ---- guarded writes --------------------------------------------------------------------------------------

    /**
     * Dispatch, in this transaction: the order row takes the room (guard: paid, open imaging order with no execution
     * row yet), then the execution row is inserted. Returns 1 only when both happened. Returns 0 with nothing written
     * when the order is not dispatchable or already has an execution.
     */
    public int assignImagingRoom(UUID orderId, UUID visitId, UUID patientId, UUID serviceId, UUID roomId,
            String imagingType, UUID orderingDoctorAccountId) {
        lockOrder(orderId);
        int assigned = update("""
                update service_orders set room_id = :roomId
                 where id = :id and service_type = 'IMAGING' and order_status = 'ORDERED' and payment_status = 'PAID'
                   and visit_id = :visitId and patient_id = :patientId and service_id = :serviceId
                   and not exists (select 1 from imaging_executions e where e.service_order_id = :id)
                """, orderId, q -> q.setParameter("roomId", roomId).setParameter("visitId", visitId)
                .setParameter("patientId", patientId).setParameter("serviceId", serviceId));
        if (assigned == 0) return 0;
        int created = update("""
                insert into imaging_executions (service_order_id, imaging_type, ordering_doctor_account_id,
                       execution_status, version, created_at, updated_at)
                values (:id, :imagingType, cast(:doctorId as uuid), 'PENDING', 0, clock_timestamp(), clock_timestamp())
                on conflict (service_order_id) do nothing
                """, orderId, q -> q.setParameter("imagingType", imagingType)
                .setParameter("doctorId", orderingDoctorAccountId));
        if (created == 0) throw new IllegalStateException("imaging execution already exists for order " + orderId);
        return created;
    }

    public int recordFirstUse(UUID orderId, long expectedVersion, UUID attemptId, UUID technicianAccountId,
            String firstUseType, UUID firstUseEventId, Instant firstUseAt, String firstUseSourceId) {
        return versioned("""
                update imaging_executions
                   set execution_status = 'IN_PROGRESS', attempt_id = :attemptId,
                       technician_account_id = :technicianId, first_use_type = :type,
                       first_use_event_id = :eventId, first_use_at = :at, first_use_source_id = :sourceId,
                       version = version + 1, updated_at = clock_timestamp()
                 where service_order_id = :id and version = :expectedVersion
                   and execution_status = 'PENDING' and attempt_id is null
                """, orderId, expectedVersion, q -> q.setParameter("attemptId", attemptId)
                .setParameter("technicianId", technicianAccountId).setParameter("type", firstUseType)
                .setParameter("eventId", firstUseEventId).setParameter("at", Timestamp.from(firstUseAt))
                .setParameter("sourceId", firstUseSourceId));
    }

    public int linkStudy(UUID orderId, long expectedVersion, UUID attemptId, String studyRef) {
        return versioned("""
                update imaging_executions
                   set execution_status = 'RESULT_AVAILABLE', study_ref = :studyRef,
                       version = version + 1, updated_at = clock_timestamp()
                 where service_order_id = :id and version = :expectedVersion
                   and execution_status = 'IN_PROGRESS' and attempt_id = :attemptId and study_ref is null
                """, orderId, expectedVersion,
                q -> q.setParameter("attemptId", attemptId).setParameter("studyRef", studyRef));
    }

    public int markReviewed(UUID orderId, long expectedVersion) {
        return versioned("""
                update imaging_executions
                   set execution_status = 'REVIEWED',
                       version = version + 1, updated_at = clock_timestamp()
                 where service_order_id = :id and version = :expectedVersion
                   and execution_status = 'RESULT_AVAILABLE'
                """, orderId, expectedVersion, q -> { });
    }

    public int cancel(UUID orderId, long expectedVersion, String cause, String reason) {
        return versioned("""
                update imaging_executions
                   set execution_status = 'CANCELLED', cancellation_cause = :cause, cancellation_reason = :reason,
                       version = version + 1, updated_at = clock_timestamp()
                 where service_order_id = :id and version = :expectedVersion and execution_status = 'PENDING'
                """, orderId, expectedVersion, q -> q.setParameter("cause", cause).setParameter("reason", reason));
    }

    /**
     * Room move of a dispatched imaging order: the execution's version guard first (PENDING, expected version), then
     * the order's room. Returns 1 only when both happened; 0 with nothing written otherwise.
     */
    public int transferRoom(UUID orderId, long expectedVersion, UUID roomId) {
        lockOrder(orderId);
        int moved = versioned("""
                update imaging_executions
                   set version = version + 1, updated_at = clock_timestamp()
                 where service_order_id = :id and version = :expectedVersion and execution_status = 'PENDING'
                """, orderId, expectedVersion, q -> { });
        if (moved == 0) return 0;
        if (update("update service_orders set room_id = :roomId where id = :id", orderId,
                q -> q.setParameter("roomId", roomId)) != 1) {
            throw new IllegalStateException("room not moved for order " + orderId);
        }
        return moved;
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    /**
     * Row lock on the order, taken before a write that depends on its execution row. It is held until commit, so the
     * "no execution yet" check that follows sees a concurrent dispatch that committed first.
     */
    private void lockOrder(UUID orderId) {
        entityManager.flush();
        entityManager.createNativeQuery("select o.id from service_orders o where o.id = :id for update")
                .setParameter("id", orderId).getResultList();
    }

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

    private static Optional<ClinicalImagingExecutionRow> single(Query query) {
        List<ClinicalImagingExecutionRow> rows = list(query);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @SuppressWarnings("unchecked")
    private static List<ClinicalImagingExecutionRow> list(Query query) {
        List<ClinicalImagingExecutionRow> result = new ArrayList<>();
        for (Tuple t : (List<Tuple>) query.getResultList()) {
            result.add(new ClinicalImagingExecutionRow(uuid(t, "id"), uuid(t, "visit_id"), uuid(t, "patient_id"),
                    uuid(t, "ordering_doctor_account_id"), uuid(t, "service_id"), uuid(t, "room_id"),
                    text(t, "imaging_type"), text(t, "body_area"), text(t, "clinical_reason"),
                    text(t, "dicom_patient_id"), text(t, "dicom_patient_issuer"),
                    text(t, "dicom_accession_number"), text(t, "dicom_accession_local_namespace"),
                    text(t, "execution_status"), text(t, "cancellation_cause"),
                    text(t, "cancellation_reason"), uuid(t, "attempt_id"), uuid(t, "technician_account_id"),
                    text(t, "first_use_type"), uuid(t, "first_use_event_id"), instant(t, "first_use_at"),
                    text(t, "first_use_source_id"), text(t, "study_ref"),
                    ((Number) t.get("execution_version")).longValue(),
                    instant(t, "created_at"), instant(t, "updated_at")));
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

    private static Instant instant(Tuple t, String column) {
        Object value = t.get(column);
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof Timestamp ts) return ts.toInstant();
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        return Instant.parse(value.toString());
    }
}
