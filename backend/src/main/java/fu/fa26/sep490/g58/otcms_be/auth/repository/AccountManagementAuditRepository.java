package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountManagementAuditJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountManagementAuditRepository extends JpaRepository<AccountManagementAuditJpaEntity, UUID> { }
