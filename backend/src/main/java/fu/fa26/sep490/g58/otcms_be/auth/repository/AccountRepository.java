package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.AccountAccessSnapshot;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.PatientAccountAdminRow;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountJpaEntity, UUID> {
    @Query("select a from AccountJpaEntity a where a.accountType in :types "
            + "and (:role is null or a.accountType = :role) "
            + "and (:active is null or (:active = true and a.status = "
            + "fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus.ACTIVE) "
            + "or (:active = false and a.status <> "
            + "fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus.ACTIVE)) "
            + "and coalesce(a.username, '') like :pattern escape '!'")
    Page<AccountJpaEntity> searchManagedStaff(
            @Param("types") java.util.Set<fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType> types,
            @Param("role") fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType role,
            @Param("active") Boolean active, @Param("pattern") String pattern, Pageable pageable);

    @Query(value = "select new fu.fa26.sep490.g58.otcms_be.auth.model.dto.PatientAccountAdminRow(" +
            "a.id, a.normalizedEmail, coalesce(p.fullName, a.displayName, a.fullName), a.status, a.createdAt) " +
            "from AccountJpaEntity a left join PatientProfileJpaEntity p on p.accountId = a.id " +
            "where a.accountType = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType.PATIENT " +
            "and (:activeOnly is null or (:activeOnly = true and a.status = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus.ACTIVE) " +
            "or (:activeOnly = false and a.status <> fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus.ACTIVE)) " +
            "and (:pattern = '%%' or lower(coalesce(a.normalizedEmail, '')) like :pattern escape '!' " +
            "or lower(coalesce(p.fullName, a.fullName, '')) like :pattern escape '!') " +
            "order by a.createdAt desc, a.id desc",
            countQuery = "select count(a) from AccountJpaEntity a left join PatientProfileJpaEntity p on p.accountId = a.id " +
                    "where a.accountType = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType.PATIENT " +
                    "and (:activeOnly is null or (:activeOnly = true and a.status = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus.ACTIVE) " +
                    "or (:activeOnly = false and a.status <> fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus.ACTIVE)) " +
                    "and (:pattern = '%%' or lower(coalesce(a.normalizedEmail, '')) like :pattern escape '!' " +
                    "or lower(coalesce(p.fullName, a.displayName, a.fullName, '')) like :pattern escape '!')")
    Page<PatientAccountAdminRow> searchPatientAccounts(@Param("pattern") String pattern,
            @Param("activeOnly") Boolean activeOnly, Pageable pageable);

    @Query("select new fu.fa26.sep490.g58.otcms_be.auth.model.dto.AccountAccessSnapshot("
            + "a.accountType, a.status, a.mustChangePassword) from AccountJpaEntity a where a.id = :id")
    Optional<AccountAccessSnapshot> findAccessSnapshotById(@Param("id") UUID id);

    @Query("select a from AccountJpaEntity a where "
            + "a.accountType = fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType.ADMIN "
            + "and (:status is null or a.status = :status) "
            + "and (lower(coalesce(a.username, '')) like :pattern escape '!' "
            + "or lower(coalesce(a.normalizedEmail, '')) like :pattern escape '!')")
    Page<AccountJpaEntity> searchAdministrators(@Param("pattern") String pattern,
            @Param("status") AccountStatus status, Pageable pageable);

    java.util.List<AccountJpaEntity> findByAccountTypeAndStatusOrderByFullNameAscIdAsc(
            fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType type, AccountStatus status);

    Optional<AccountJpaEntity> findByIdAndAccountType(UUID id,
            fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType accountType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional
    Optional<AccountJpaEntity> findByUsername(String username);
    boolean existsByUsername(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional
    Optional<AccountJpaEntity> findByNormalizedEmail(String normalizedEmail);
    boolean existsByNormalizedEmail(String normalizedEmail);
    @Modifying
    @Query(value = "insert into accounts (id, normalized_email, account_type, status, password_hash, full_name, failed_credential_count, created_at, updated_at) " +
            "values (:id, :email, :accountType, :status, :passwordHash, :fullName, 0, :createdAt, :updatedAt) " +
            "on conflict (normalized_email) do nothing", nativeQuery = true)
    int insertIfEmailAvailable(@Param("id") UUID id, @Param("email") String email,
                               @Param("accountType") String accountType, @Param("status") String status,
                               @Param("passwordHash") String passwordHash, @Param("fullName") String fullName,
                               @Param("createdAt") Instant createdAt, @Param("updatedAt") Instant updatedAt);
    boolean existsByIdAndAccountType(UUID id, fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType accountType);
    boolean existsByIdAndAccountTypeAndStatus(UUID id, fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType accountType, AccountStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountJpaEntity a where a.id = :id")
    Optional<AccountJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Page<AccountJpaEntity> findByStatusAndCreatedAtBefore(AccountStatus status, Instant cutoff, Pageable pageable);
}
