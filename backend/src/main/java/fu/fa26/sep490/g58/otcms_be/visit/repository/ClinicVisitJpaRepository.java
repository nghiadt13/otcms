package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicVisitJpaRepository extends JpaRepository<ClinicVisitJpaEntity, UUID> {
    @Query("""
            select v from ClinicVisitJpaEntity v
            where v.patientProfileId = :patientId and v.id <> :currentId and v.status = 'CLOSED' and v.arrivedAt <= :before
                and exists (select a.id from VisitCareAssignmentJpaEntity a where a.visitId = v.id and a.status = 'COMPLETED')
            order by v.arrivedAt desc, v.id desc
            """)
    org.springframework.data.domain.Page<ClinicVisitJpaEntity> findHistoricalVisits(
            @Param("patientId") UUID patientProfileId, @Param("currentId") UUID currentVisitId,
            @Param("before") java.time.Instant before, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select visit from ClinicVisitJpaEntity visit where visit.id = :visitId")
    Optional<ClinicVisitJpaEntity> findByIdForUpdate(@Param("visitId") UUID visitId);

    /** Walk-in replay lookup; same scope as uk_clinic_visits_walk_in_idempotency (creator, key). */
    Optional<ClinicVisitJpaEntity> findByCreatedByAccountIdAndWalkInIdempotencyKey(UUID createdByAccountId,
            String walkInIdempotencyKey);
}
