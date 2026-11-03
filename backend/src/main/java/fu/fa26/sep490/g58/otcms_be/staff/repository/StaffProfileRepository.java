package fu.fa26.sep490.g58.otcms_be.staff.repository;

import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffProfileRepository extends JpaRepository<StaffProfileJpaEntity, UUID> {
    interface AccountSummary {
        UUID getAccountId();
        UUID getId();
        String getDisplayName();
    }

    @org.springframework.data.jpa.repository.Query("select p.accountId as accountId, p.id as id, "
            + "p.displayName as displayName from StaffProfileJpaEntity p where p.accountId in :accountIds")
    java.util.List<AccountSummary> findAccountSummaries(
            @org.springframework.data.repository.query.Param("accountIds") java.util.Set<UUID> accountIds);

    boolean existsByAccountId(UUID accountId);
    Optional<StaffProfileJpaEntity> findByAccountId(UUID accountId);
}
