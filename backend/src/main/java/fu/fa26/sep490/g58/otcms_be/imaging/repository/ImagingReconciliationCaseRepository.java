package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingReconciliationCaseJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagingReconciliationCaseRepository
        extends JpaRepository<ImagingReconciliationCaseJpaEntity, UUID> {
    List<ImagingReconciliationCaseJpaEntity> findAllByStateOrderByUpdatedAtAscIdAsc(String state);
    List<ImagingReconciliationCaseJpaEntity> findAllByOperationIdAndStateNot(UUID operationId, String state);
    List<ImagingReconciliationCaseJpaEntity> findAllByEventIdAndStateNot(UUID eventId, String state);
}
