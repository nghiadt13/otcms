package fu.fa26.sep490.g58.otcms_be.clinical.repository;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.UUID;
public interface ServiceOrderRepository extends JpaRepository<ServiceOrderJpaEntity,UUID>{
    interface LaboratoryAssignmentRow {
        UUID getRoomId();
        String getLabStatus();
    }
    interface LaboratoryQueueRow {
        UUID getOrderId(); UUID getRoomId(); String getLabStatus();
    }
    @org.springframework.data.jpa.repository.Query(value = "select id as \"orderId\", room_id as \"roomId\", lab_status as \"labStatus\" "
            + "from clinical_service_orders where service_type = 'LABORATORY' "
            + "and ((ordered_at >= :fromTime and ordered_at < :toTime) "
            + "or lab_status in ('ASSIGNED', 'COLLECTED', 'SENT', 'REJECTED'))", nativeQuery = true)
    List<LaboratoryQueueRow> findLaboratoryAssignments(@org.springframework.data.repository.query.Param("fromTime") java.time.Instant fromTime,
            @org.springframework.data.repository.query.Param("toTime") java.time.Instant toTime);
    @org.springframework.data.jpa.repository.Query(value = "select room_id as \"roomId\", lab_status as \"labStatus\" "
            + "from clinical_service_orders where id = :orderId and service_type = 'LABORATORY'", nativeQuery = true)
    java.util.Optional<LaboratoryAssignmentRow> findLaboratoryAssignment(
            @org.springframework.data.repository.query.Param("orderId") UUID orderId);
    @org.springframework.data.jpa.repository.Query("select o from ServiceOrderJpaEntity o where o.requestingDoctorId = :doctorId "
            + "and o.submitIdempotencyKey = :key and o.roundHead = true")
    java.util.Optional<ServiceOrderJpaEntity> findRoundBySubmissionKey(
            @org.springframework.data.repository.query.Param("doctorId") UUID doctorId,
            @org.springframework.data.repository.query.Param("key") String key);
    @org.springframework.data.jpa.repository.Query("select o from ServiceOrderJpaEntity o where o.orderingRoundId = :roundId")
    List<ServiceOrderJpaEntity> findLaboratoryRoundOrders(
            @org.springframework.data.repository.query.Param("roundId") UUID roundId);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = """
            update clinical_service_orders set room_id = :roomId, lab_status = 'ASSIGNED',
                updated_at = :now, version = version + 1
            where id = :orderId and service_type = 'LABORATORY' and order_status = 'ORDERED'
                and payment_status = 'PAID' and lab_status is null
            """, nativeQuery = true)
    int assignLaboratoryRoom(@org.springframework.data.repository.query.Param("orderId") UUID orderId,
            @org.springframework.data.repository.query.Param("roomId") UUID roomId,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = "update clinical_service_orders set lab_status = :next, updated_at = :now, version = version + 1 where id = :orderId and service_type = 'LABORATORY' and payment_status = 'PAID' and order_status = 'ORDERED' and lab_status = :expected", nativeQuery = true)
    int transitionLabStatus(@org.springframework.data.repository.query.Param("orderId") UUID orderId,
            @org.springframework.data.repository.query.Param("expected") String expected,
            @org.springframework.data.repository.query.Param("next") String next,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = "update clinical_service_orders set lab_status = 'RESULT_AVAILABLE', order_status = 'RESULTS_AVAILABLE', updated_at = :now, version = version + 1 where id = :orderId and service_type = 'LABORATORY' and payment_status = 'PAID' and order_status = 'ORDERED' and lab_status = 'SENT'", nativeQuery = true)
    int releaseLabResult(@org.springframework.data.repository.query.Param("orderId") UUID orderId, @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    @org.springframework.data.jpa.repository.Query("select o from ServiceOrderJpaEntity o where o.visitId = :visitId "
            + "and o.serviceType = :serviceType order by o.roundNumber, o.orderCode")
    List<ServiceOrderJpaEntity> findVisitOrdersByType(
            @org.springframework.data.repository.query.Param("visitId") UUID visitId,
            @org.springframework.data.repository.query.Param("serviceType") fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType serviceType);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = """
            update clinical_service_orders
               set doctor_result_assessment = :assessment,
                   doctor_result_assessed_by_account_id = :accountId,
                   doctor_result_assessed_at = :assessedAt,
                   updated_at = :assessedAt,
                   version = version + 1
             where id = :orderId and visit_id = :visitId
               and service_type = 'LABORATORY' and order_status = 'RESULTS_AVAILABLE'
               and lab_status = 'RESULT_AVAILABLE'
               and doctor_result_assessment is null
               and doctor_result_assessed_by_account_id is null
               and doctor_result_assessed_at is null
            """, nativeQuery = true)
    int recordDoctorResultAssessment(@org.springframework.data.repository.query.Param("orderId") UUID orderId,
            @org.springframework.data.repository.query.Param("visitId") UUID visitId,
            @org.springframework.data.repository.query.Param("assessment") String assessment,
            @org.springframework.data.repository.query.Param("accountId") UUID accountId,
            @org.springframework.data.repository.query.Param("assessedAt") java.time.Instant assessedAt);
    List<ServiceOrderJpaEntity> findByOrderingRoundId(UUID roundId);
    List<ServiceOrderJpaEntity> findByOrderingRoundIdIn(java.util.Collection<UUID> roundIds);
    List<ServiceOrderJpaEntity> findByVisitId(UUID visitId);
    java.util.Optional<ServiceOrderJpaEntity> findFirstByOrderingRoundIdAndRoundHeadTrue(UUID roundId);
    java.util.Optional<ServiceOrderJpaEntity> findByRequestingDoctorIdAndSubmitIdempotencyKeyAndRoundHeadTrue(UUID doctorId, String submitIdempotencyKey);
    java.util.Optional<ServiceOrderJpaEntity> findTopByVisitIdAndRoundHeadTrueOrderByRoundNumberDesc(UUID visitId);
    org.springframework.data.domain.Page<ServiceOrderJpaEntity> findAllByRoundHeadTrue(org.springframework.data.domain.Pageable pageable);
    List<ServiceOrderJpaEntity> findByVisitIdAndRoundHeadTrueOrderByRoundNumberAsc(UUID visitId);
}
