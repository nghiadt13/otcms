package fu.fa26.sep490.g58.otcms_be.content.repository;

import fu.fa26.sep490.g58.otcms_be.content.model.entity.WebsiteImageJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsiteImageRepository extends JpaRepository<WebsiteImageJpaEntity, UUID> {
}
