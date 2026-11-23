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
    @org.springframework.data.jpa.repository.Query(value = "select o.id as \"orderId\", o.room_id as \"roomId\", o.lab_status as \"labStatus\" "
            + "from service_orders o join service_order_rounds r on r.id = o.ordering_round_id "
            + "where o.service_type = 'LABORATORY' "
            + "and ((r.ordered_at >= :fromTime and r.ordered_at < :toTime) "
            + "or o.lab_status in ('ASSIGNED', 'COLLECTED', 'REJECTED'))", nativeQuery = true)
    List<LaboratoryQueueRow> findLaboratoryAssignments(@org.springframework.data.repository.query.Param("fromTime") java.time.Instant fromTime,
            @org.springframework.data.repository.query.Param("toTime") java.time.Instant toTime);
    @org.springframework.data.jpa.repository.Query(value = "select room_id as \"roomId\", lab_status as \"labStatus\" "
            + "from service_orders where id = :orderId and service_type = 'LABORATORY'", nativeQuery = true)
    java.util.Optional<LaboratoryAssignmentRow> findLaboratoryAssignment(
            @org.springframework.data.repository.query.Param("orderId") UUID orderId);
    @org.springframework.data.jpa.repository.Query("select o from ServiceOrderJpaEntity o where o.orderingRoundId = :roundId")
    List<ServiceOrderJpaEntity> findLaboratoryRoundOrders(
            @org.springframework.data.repository.query.Param("roundId") UUID roundId);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = """
            update service_orders set room_id = :roomId, lab_status = 'ASSIGNED',
                updated_at = :now, version = version + 1
            where id = :orderId and service_type = 'LABORATORY' and order_status = 'ORDERED'
                and payment_status = 'PAID' and lab_status is null
            """, nativeQuery = true)
    int assignLaboratoryRoom(@org.springframework.data.repository.query.Param("orderId") UUID orderId,
            @org.springframework.data.repository.query.Param("roomId") UUID roomId,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = "update service_orders set lab_status = :next, updated_at = :now, version = version + 1 where id = :orderId and service_type = 'LABORATORY' and payment_status = 'PAID' and order_status = 'ORDERED' and lab_status = :expected", nativeQuery = true)
    int transitionLabStatus(@org.springframework.data.repository.query.Param("orderId") UUID orderId,
            @org.springframework.data.repository.query.Param("expected") String expected,
            @org.springframework.data.repository.query.Param("next") String next,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = "update service_orders set lab_status = 'RESULT_AVAILABLE', order_status = 'RESULTS_AVAILABLE', updated_at = :now, version = version + 1 where id = :orderId and service_type = 'LABORATORY' and payment_status = 'PAID' and order_status = 'ORDERED' and lab_status = 'COLLECTED'", nativeQuery = true)
    int releaseLabResult(@org.springframework.data.repository.query.Param("orderId") UUID orderId, @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    @org.springframework.data.jpa.repository.Query("select o from ServiceOrderJpaEntity o, ClinicalOrderingRoundJpaEntity r where r.id = o.orderingRoundId "
            + "and o.visitId = :visitId and o.serviceType = :serviceType order by r.roundNumber, o.orderCode")
    List<ServiceOrderJpaEntity> findVisitOrdersByType(
            @org.springframework.data.repository.query.Param("visitId") UUID visitId,
            @org.springframework.data.repository.query.Param("serviceType") fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType serviceType);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = """
            update service_orders
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
}
