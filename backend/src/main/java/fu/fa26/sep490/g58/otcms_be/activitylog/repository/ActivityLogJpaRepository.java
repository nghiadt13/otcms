package fu.fa26.sep490.g58.otcms_be.activitylog.repository;

import fu.fa26.sep490.g58.otcms_be.activitylog.model.entity.ActivityLogViewJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityLogJpaRepository extends JpaRepository<ActivityLogViewJpaEntity, String> {
    interface SafeActivityRow {
        String getId();
        Instant getOccurredAt();
        String getActorUsername();
        String getActorRole();
        String getAction();
        String getRecordType();
        String getRecordLabel();
        String getCorrectionReason();
        String getRecordCode();
    }

    String SAFE_SELECT = "SELECT e.id AS id, e.occurred_at AS \"occurredAt\", "
            + "coalesce(a.username, a.normalized_email, e.actor_account_id::text, 'system') AS \"actorUsername\", "
            + "CASE a.account_type WHEN 'OWNER' THEN 'CLINIC_OWNER' WHEN 'DOCTOR' THEN 'ORTHOPEDIC_DOCTOR' "
            + "ELSE a.account_type END AS \"actorRole\", e.action AS action, e.category AS \"recordType\", "
            + "e.record_label AS \"recordLabel\", e.correction_reason AS \"correctionReason\", e.record_id::text AS \"recordCode\" ";
    String PAYMENT_HOLD_FILTER = " AND NOT (e.id LIKE 'appointment:%' AND EXISTS (SELECT 1 FROM appointments h "
            + "WHERE h.id=e.record_id AND h.status IN ('AWAITING_PAYMENT','PAYMENT_EXPIRED'))) ";
    String FILTERS = " FROM activity_log_entries e LEFT JOIN accounts a ON a.id=e.actor_account_id "
            + "WHERE strpos(lower(coalesce(e.record_label, '')), lower(:record)) > 0 "
            + "AND strpos(lower(coalesce(a.username, a.normalized_email, e.actor_account_id::text, 'system')), lower(:actor)) > 0 "
            + PAYMENT_HOLD_FILTER
            + "AND (:category = '' OR e.category=:category) "
            + "AND (cast(:fromAt AS timestamptz) IS NULL OR e.occurred_at >= :fromAt) "
            + "AND (cast(:untilAt AS timestamptz) IS NULL OR e.occurred_at < :untilAt) ";

    @Query(value = SAFE_SELECT + FILTERS + " ORDER BY e.occurred_at DESC, e.id DESC LIMIT :size OFFSET :offset",
            nativeQuery = true)
    List<SafeActivityRow> findSafePage(@Param("fromAt") Instant from, @Param("untilAt") Instant until,
            @Param("category") String category, @Param("record") String record, @Param("actor") String actor,
            @Param("size") int size, @Param("offset") long offset);

    @Query(value = "SELECT count(*) " + FILTERS, nativeQuery = true)
    long countSafe(@Param("fromAt") Instant from, @Param("untilAt") Instant until,
            @Param("category") String category, @Param("record") String record, @Param("actor") String actor);

    @Query(value = SAFE_SELECT + " FROM activity_log_entries e LEFT JOIN accounts a ON a.id=e.actor_account_id "
            + "WHERE e.id=:id " + PAYMENT_HOLD_FILTER, nativeQuery = true)
    Optional<SafeActivityRow> findSafeById(@Param("id") String id);
}
