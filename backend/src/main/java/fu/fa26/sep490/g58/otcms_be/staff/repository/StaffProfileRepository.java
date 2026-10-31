package fu.fa26.sep490.g58.otcms_be.staff.repository;

import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffProfileRepository extends JpaRepository<StaffProfileJpaEntity, UUID> {
    boolean existsByAccountId(UUID accountId);
    Optional<StaffProfileJpaEntity> findByAccountId(UUID accountId);
}
