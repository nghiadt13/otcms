package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountJpaEntity, UUID> {
    Optional<AccountJpaEntity> findByNormalizedEmail(String normalizedEmail);
    boolean existsByNormalizedEmail(String normalizedEmail);
    @Modifying
    @Query(value = "insert into accounts (id, normalized_email, account_type, status, password_hash, failed_credential_count, created_at, updated_at) " +
            "values (:id, :email, :accountType, :status, :passwordHash, 0, :createdAt, :updatedAt) " +
            "on conflict (normalized_email) do nothing", nativeQuery = true)
    int insertIfEmailAvailable(@Param("id") UUID id, @Param("email") String email,
                               @Param("accountType") String accountType, @Param("status") String status,
                               @Param("passwordHash") String passwordHash,
                               @Param("createdAt") Instant createdAt, @Param("updatedAt") Instant updatedAt);
    boolean existsByIdAndAccountType(UUID id, fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType accountType);
    boolean existsByIdAndAccountTypeAndStatus(UUID id, fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType accountType, AccountStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountJpaEntity a where a.id = :id")
    Optional<AccountJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Page<AccountJpaEntity> findByStatusAndCreatedAtBefore(AccountStatus status, Instant cutoff, Pageable pageable);
}
