package fu.fa26.sep490.g58.otcms_be.clinical.repository;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalMedicalDocumentType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalMedicalDocumentJpaEntity;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ClinicalMedicalDocumentRepository extends JpaRepository<ClinicalMedicalDocumentJpaEntity, UUID> {
    List<ClinicalMedicalDocumentJpaEntity> findByPatientProfileIdAndVisitIdInAndReleasedAtIsNotNullOrderByCreatedAtDescIdDesc(
            UUID patientProfileId, Collection<UUID> visitIds);
    List<ClinicalMedicalDocumentJpaEntity> findByVisitIdOrderByCreatedAtDescIdDesc(UUID visitId);
    List<ClinicalMedicalDocumentJpaEntity> findByVisitIdAndReleasedAtIsNotNullOrderByCreatedAtDescIdDesc(UUID visitId);
    List<ClinicalMedicalDocumentJpaEntity> findByVisitIdAndDocumentTypeOrderByCreatedAtDescIdDesc(UUID visitId, ClinicalMedicalDocumentType type);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into clinical_medical_documents (id, visit_id, patient_profile_id, document_type, title,
                original_filename, media_type, byte_size, created_at, updated_at, version, source)
            values (:id, :visitId, :patientId, 'CLINIC_EXAMINATION_REPORT', 'Phiếu kết quả khám',
                'phieu-ket-qua.pdf', 'application/pdf', :size, :now, :now, 1, 'GENERATED')
            on conflict (visit_id) where document_type = 'CLINIC_EXAMINATION_REPORT' and status = 'ACTIVE' do nothing
            """, nativeQuery = true)
    int insertGeneratedResultSheet(@Param("id") UUID id, @Param("visitId") UUID visitId,
            @Param("patientId") UUID patientId, @Param("size") long size, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update clinical_medical_documents set released_at = :now, released_by_doctor_profile_id = :doctorId,
                updated_at = :now where id = :id and source = 'GENERATED' and released_at is null
            """, nativeQuery = true)
    int releaseGeneratedResultSheet(@Param("id") UUID id, @Param("doctorId") UUID doctorId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into clinical_medical_documents (id, visit_id, patient_profile_id, document_type, title,
                original_filename, media_type, byte_size, created_at, updated_at, version)
            values (:id, :visitId, :patientId, :type, :title, :filename, :mediaType, :size, :now, :now, 1)
            on conflict (visit_id) where document_type = 'CLINIC_EXAMINATION_REPORT' and status = 'ACTIVE' do nothing
            """, nativeQuery = true)
    int insertDocument(@Param("id") UUID id, @Param("visitId") UUID visitId, @Param("patientId") UUID patientId,
            @Param("type") String type, @Param("title") String title, @Param("filename") String filename,
            @Param("mediaType") String mediaType, @Param("size") long size, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ClinicalMedicalDocumentJpaEntity d set d.title = :title, d.originalFilename = :filename,
                d.mediaType = :mediaType, d.byteSize = :size, d.updatedAt = :now, d.version = d.version + 1
            where d.id = :id and d.version = :version and d.releasedAt is null
            """)
    int replace(@Param("id") UUID id, @Param("version") long version, @Param("title") String title,
            @Param("filename") String filename, @Param("mediaType") String mediaType, @Param("size") long size, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ClinicalMedicalDocumentJpaEntity d set d.releasedAt = :now, d.releasedByDoctorProfileId = :doctorId,
                d.updatedAt = :now, d.version = d.version + 1
            where d.id = :id and d.version = :version and d.releasedAt is null
            """)
    int release(@Param("id") UUID id, @Param("version") long version, @Param("doctorId") UUID doctorProfileId, @Param("now") Instant now);
}
