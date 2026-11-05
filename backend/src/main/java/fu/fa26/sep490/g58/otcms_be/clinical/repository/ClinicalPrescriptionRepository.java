package fu.fa26.sep490.g58.otcms_be.clinical.repository;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalPrescriptionJpaEntity;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ClinicalPrescriptionRepository extends JpaRepository<ClinicalPrescriptionJpaEntity, UUID> {
    org.springframework.data.domain.Page<ClinicalPrescriptionJpaEntity> findByPatientProfileIdInAndStatus(
            Collection<UUID> patientIds, ClinicalPrescriptionStatus status, org.springframework.data.domain.Pageable pageable);
    Optional<ClinicalPrescriptionJpaEntity> findByVisitId(UUID visitId);
    List<ClinicalPrescriptionJpaEntity> findByPatientProfileIdAndVisitIdInAndStatusOrderByIssuedAtDescIdDesc(
            UUID patientProfileId, Collection<UUID> visitIds, ClinicalPrescriptionStatus status);
    List<ClinicalPrescriptionJpaEntity> findByPatientProfileIdAndStatusOrderByIssuedAtDescIdDesc(UUID patientProfileId, ClinicalPrescriptionStatus status);
    List<ClinicalPrescriptionJpaEntity> findByVisitIdAndStatusOrderByIssuedAtDescIdDesc(UUID visitId, ClinicalPrescriptionStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into clinical_prescriptions (id, visit_id, patient_profile_id, doctor_profile_id, status,
                created_at, updated_at, version)
            values (:id, :visitId, :patientId, :doctorId, 'DRAFT', :now, :now, 1)
            on conflict (visit_id) do nothing
            """, nativeQuery = true)
    int insertDraft(@Param("id") UUID id, @Param("visitId") UUID visitId, @Param("patientId") UUID patientProfileId,
            @Param("doctorId") UUID doctorProfileId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ClinicalPrescriptionJpaEntity p set p.version = p.version + 1, p.updatedAt = :now, p.doctorProfileId = :doctorId
            where p.id = :id and p.version = :expectedVersion
                and p.status = fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus.DRAFT
            """)
    int advanceDraftVersion(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion,
            @Param("doctorId") UUID doctorProfileId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ClinicalPrescriptionJpaEntity p set p.version = p.version + 1, p.updatedAt = :now,
                p.status = fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus.ISSUED,
                p.issuedAt = :now, p.issuanceKey = :key, p.issuanceExpectedVersion = :expectedVersion, p.doctorProfileId = :doctorId
            where p.id = :id and p.version = :expectedVersion
                and p.status = fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus.DRAFT
            """)
    int issueDraft(@Param("id") UUID id, @Param("expectedVersion") long expectedVersion,
            @Param("key") String key, @Param("doctorId") UUID doctorProfileId, @Param("now") Instant now);
}
