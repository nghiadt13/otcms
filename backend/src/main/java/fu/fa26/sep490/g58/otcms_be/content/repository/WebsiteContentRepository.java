package fu.fa26.sep490.g58.otcms_be.content.repository;

import fu.fa26.sep490.g58.otcms_be.content.model.entity.WebsiteContentJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebsiteContentRepository extends JpaRepository<WebsiteContentJpaEntity, UUID>, JpaSpecificationExecutor<WebsiteContentJpaEntity> {
    java.util.Optional<WebsiteContentJpaEntity> findFirstByTypeAndStatusOrderByUpdatedAtDescIdDesc(
        fu.fa26.sep490.g58.otcms_be.content.model.ContentType type, String status);
}
