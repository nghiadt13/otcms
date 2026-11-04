package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingAuditEventJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagingAuditEventRepository extends JpaRepository<ImagingAuditEventJpaEntity, UUID> {
    List<ImagingAuditEventJpaEntity> findAllByRequestIdOrderByRecordedAtDescIdDesc(UUID requestId);
    List<ImagingAuditEventJpaEntity> findAllByStudyRefOrderByRecordedAtDescIdDesc(String studyRef);
}
