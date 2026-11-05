package fu.fa26.sep490.g58.otcms_be.clinical.repository;

import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalPrescriptionItemJpaEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ClinicalPrescriptionItemRepository extends JpaRepository<ClinicalPrescriptionItemJpaEntity, UUID> {
    List<ClinicalPrescriptionItemJpaEntity> findByPrescriptionIdOrderByLineNumberAsc(UUID prescriptionId);
    List<ClinicalPrescriptionItemJpaEntity> findByPrescriptionIdInOrderByPrescriptionIdAscLineNumberAsc(Collection<UUID> prescriptionIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ClinicalPrescriptionItemJpaEntity i where i.prescriptionId = :prescriptionId")
    int deleteItems(@Param("prescriptionId") UUID prescriptionId);
}
