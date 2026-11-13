package fu.fa26.sep490.g58.otcms_be.staff.repository;

import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Staff profiles, doctors included (final-db V110 merged the old doctor profile table here).
 * Doctor lookups must use the *DoctorTrue / *Doctors methods; never plain findById/existsById for a doctor.
 */
public interface StaffProfileRepository extends JpaRepository<StaffProfileJpaEntity, UUID> {
    interface AccountSummary {
        UUID getAccountId();
        UUID getId();
        String getDisplayName();
    }

    @Query("select p.accountId as accountId, p.id as id, "
            + "p.displayName as displayName from StaffProfileJpaEntity p where p.accountId in :accountIds")
    List<AccountSummary> findAccountSummaries(
            @org.springframework.data.repository.query.Param("accountIds") java.util.Set<UUID> accountIds);

    boolean existsByAccountId(UUID accountId);
    Optional<StaffProfileJpaEntity> findByAccountId(UUID accountId);

    // Doctor-only lookups (is_doctor = true).
    Optional<StaffProfileJpaEntity> findByIdAndDoctorTrue(UUID id);
    Optional<StaffProfileJpaEntity> findByAccountIdAndDoctorTrue(UUID accountId);
    boolean existsByIdAndDoctorTrue(UUID id);
    boolean existsByAccountIdAndDoctorTrue(UUID accountId);
    List<StaffProfileJpaEntity> findAllByIdInAndDoctorTrue(java.util.Collection<UUID> ids);

    @Query("select p from StaffProfileJpaEntity p where p.doctor = true order by p.displayName, p.id")
    List<StaffProfileJpaEntity> findAllDoctors();

    /** Doctors shown in the public directory. */
    @Query("select p from StaffProfileJpaEntity p where p.doctor = true and p.publiclyVisible = true "
            + "and p.inActiveDirectory = true order by p.displayName, p.id")
    List<StaffProfileJpaEntity> findPublicDoctors();

    /** Doctors in the active directory that accept new bookings. */
    @Query("select p from StaffProfileJpaEntity p where p.doctor = true and p.inActiveDirectory = true "
            + "and p.acceptingNewBookings = true order by p.displayName, p.id")
    List<StaffProfileJpaEntity> findAcceptingDoctors();
}
