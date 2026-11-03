package fu.fa26.sep490.g58.otcms_be.content.repository;

import fu.fa26.sep490.g58.otcms_be.content.model.entity.ClinicAnnouncementJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClinicAnnouncementRepository extends JpaRepository<ClinicAnnouncementJpaEntity, UUID>, JpaSpecificationExecutor<ClinicAnnouncementJpaEntity> {
}
