package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagnosticRequestJpaRepository extends JpaRepository<DiagnosticRequestJpaEntity, UUID> {
    List<DiagnosticRequestJpaEntity> findAllByVisitIdOrderByCreatedAtDescIdDesc(UUID visitId);

    Page<DiagnosticRequestJpaEntity> findAllByVisitIdOrderByCreatedAtDescIdDesc(UUID visitId, Pageable pageable);

    Page<DiagnosticRequestJpaEntity> findAllByVisitIdAndPatientIdOrderByCreatedAtDescIdDesc(
            UUID visitId, UUID patientId, Pageable pageable);

    Page<DiagnosticRequestJpaEntity> findAllByPatientIdOrderByCreatedAtDescIdDesc(UUID patientId, Pageable pageable);

    Page<DiagnosticRequestJpaEntity> findAllByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(
            UUID roomId, Collection<DiagnosticRequestStatus> statuses, Pageable pageable);

    Page<DiagnosticRequestJpaEntity> findAllByVisitIdAndOrderingDoctorIdOrderByCreatedAtDescIdDesc(
            UUID visitId, UUID orderingDoctorId, Pageable pageable);

    List<DiagnosticRequestJpaEntity> findAllByPatientIdOrderByCreatedAtDescIdDesc(UUID patientId);

    List<DiagnosticRequestJpaEntity> findAllByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            DiagnosticRequestStatus status, Instant createdBefore);

    Page<DiagnosticRequestJpaEntity> findAllByStatusInOrderByCreatedAtAscIdAsc(
            Collection<DiagnosticRequestStatus> statuses, Pageable pageable);

    Page<DiagnosticRequestJpaEntity> findAllByStatusInOrderByCreatedAtDescIdDesc(
            Collection<DiagnosticRequestStatus> statuses, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from DiagnosticRequestJpaEntity request where request.id = :requestId")
    Optional<DiagnosticRequestJpaEntity> findByIdForUpdate(@Param("requestId") UUID requestId);
}
