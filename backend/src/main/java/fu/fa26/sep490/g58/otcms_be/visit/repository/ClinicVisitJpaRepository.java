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
            where v.patientProfileId in :patientIds
                and (v.status = 'OPEN' or (v.status = 'CLOSED' and exists (
                    select a.id from VisitCareAssignmentJpaEntity a
                    where a.visitId = v.id and a.status = 'COMPLETED')))
            order by v.arrivedAt desc, v.id desc
            """)
    org.springframework.data.domain.Page<ClinicVisitJpaEntity> findPatientPortalHistory(
            @Param("patientIds") java.util.Collection<UUID> patientIds,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            select v from ClinicVisitJpaEntity v
            where v.id = :visitId and v.patientProfileId in :patientIds
                and (v.status = 'OPEN' or (v.status = 'CLOSED' and exists (
                    select a.id from VisitCareAssignmentJpaEntity a
                    where a.visitId = v.id and a.status = 'COMPLETED')))
            """)
    Optional<ClinicVisitJpaEntity> findPatientPortalVisit(
            @Param("visitId") UUID visitId, @Param("patientIds") java.util.Collection<UUID> patientIds);

    @Query("""
            select v.id from ClinicVisitJpaEntity v
            where v.patientProfileId = :patientId
                and (v.status = 'OPEN' or (v.status = 'CLOSED' and exists (
                    select a.id from VisitCareAssignmentJpaEntity a
                    where a.visitId = v.id and a.status = 'COMPLETED')))
                and (v.arrivedAt < :arrivedAt or (v.arrivedAt = :arrivedAt and v.id < :visitId))
            order by v.arrivedAt desc, v.id desc
            """)
    java.util.List<UUID> findPatientPortalPreviousVisit(
            @Param("patientId") UUID patientId, @Param("arrivedAt") java.time.Instant arrivedAt,
            @Param("visitId") UUID visitId, org.springframework.data.domain.Pageable pageable);

    @Query("""
            select v.id from ClinicVisitJpaEntity v
            where v.patientProfileId = :patientId
                and (v.status = 'OPEN' or (v.status = 'CLOSED' and exists (
                    select a.id from VisitCareAssignmentJpaEntity a
                    where a.visitId = v.id and a.status = 'COMPLETED')))
                and (v.arrivedAt > :arrivedAt or (v.arrivedAt = :arrivedAt and v.id > :visitId))
            order by v.arrivedAt asc, v.id asc
            """)
    java.util.List<UUID> findPatientPortalNextVisit(
            @Param("patientId") UUID patientId, @Param("arrivedAt") java.time.Instant arrivedAt,
            @Param("visitId") UUID visitId, org.springframework.data.domain.Pageable pageable);

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
}
