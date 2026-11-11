package fu.fa26.sep490.g58.otcms_be.clinical.repository;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ServiceOrderJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrderJpaEntity, UUID> {
    List<ServiceOrderJpaEntity> findByOrderingRoundId(UUID roundId);
    List<ServiceOrderJpaEntity> findByOrderingRoundIdIn(java.util.Collection<UUID> roundIds);
    List<ServiceOrderJpaEntity> findByVisitId(UUID visitId);

    List<ServiceOrderJpaEntity> findAllByPatientIdAndServiceTypeAndPaymentStatusAndOrderStatusOrderByCreatedAtAscIdAsc(
            UUID patientId, ClinicalServiceType serviceType, ServiceOrderPaymentStatus paymentStatus,
            ServiceOrderStatus orderStatus);

    List<ServiceOrderJpaEntity> findAllByServiceTypeAndPaymentStatusAndOrderStatusOrderByCreatedAtAscIdAsc(
            ClinicalServiceType serviceType, ServiceOrderPaymentStatus paymentStatus, ServiceOrderStatus orderStatus);
}
