package fu.fa26.sep490.g58.otcms_be.diagnostic.repository;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.LaboratorySampleJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaboratorySampleRepository extends JpaRepository<LaboratorySampleJpaEntity, UUID> {
    @Query("select s from LaboratorySampleJpaEntity s where s.serviceOrderId = :orderId order by s.attemptNumber desc")
    List<LaboratorySampleJpaEntity> findAttempts(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LaboratorySampleJpaEntity s where s.id = :sampleId and s.serviceOrderId = :orderId")
    Optional<LaboratorySampleJpaEntity> findAttemptForUpdate(@Param("sampleId") UUID sampleId,
                                                             @Param("orderId") UUID orderId);
}
