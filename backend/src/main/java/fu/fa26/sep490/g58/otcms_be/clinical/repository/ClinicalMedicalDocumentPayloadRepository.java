package fu.fa26.sep490.g58.otcms_be.clinical.repository;

import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalMedicalDocumentPayloadJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ClinicalMedicalDocumentPayloadRepository extends JpaRepository<ClinicalMedicalDocumentPayloadJpaEntity, UUID> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into clinical_medical_document_payloads (document_id, content)
            select :id, :content where exists (select 1 from clinical_medical_documents d where d.id = :id and d.released_at is null)
            on conflict (document_id) do update set content = excluded.content
            """, nativeQuery = true)
    int put(@Param("id") UUID documentId, @Param("content") byte[] content);
}
