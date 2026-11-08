package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitImagingResultReceiptJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VisitImagingResultReceiptRepository extends JpaRepository<VisitImagingResultReceiptJpaEntity, UUID> {
    @Modifying
    @Query(value = """
            insert into visit_imaging_result_receipts
                (event_id, request_id, visit_id, patient_profile_id, event_type, payload_sha256, received_at)
            values (:event, :request, :visit, :patient, :type, :hash, :now)
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int insertReceipt(@Param("event") UUID event, @Param("request") UUID request, @Param("visit") UUID visit,
            @Param("patient") UUID patient, @Param("type") String type, @Param("hash") String hash, @Param("now") Instant now);
}
