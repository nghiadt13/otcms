package fu.fa26.sep490.g58.otcms_be.content.repository;

import fu.fa26.sep490.g58.otcms_be.content.model.entity.ContentAuditJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentAuditRepository extends JpaRepository<ContentAuditJpaEntity, UUID> {
}
