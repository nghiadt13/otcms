package fu.fa26.sep490.g58.otcms_be.billing.repository;

import fu.fa26.sep490.g58.otcms_be.billing.model.entity.InvoiceJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<InvoiceJpaEntity, UUID> {
    Optional<InvoiceJpaEntity> findFirstByVisitId(UUID visitId);
    Optional<InvoiceJpaEntity> findFirstByAppointmentId(UUID appointmentId);
    java.util.List<InvoiceJpaEntity> findAllByAppointmentIdIn(Collection<UUID> appointmentIds);
    Page<InvoiceJpaEntity> findAllByPatientProfileIdIn(Collection<UUID> patientProfileIds, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from InvoiceJpaEntity invoice where invoice.id = :invoiceId")
    Optional<InvoiceJpaEntity> findByIdForUpdate(@Param("invoiceId") UUID invoiceId);
}
