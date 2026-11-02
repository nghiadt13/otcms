package fu.fa26.sep490.g58.otcms_be.staff.repository;

import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorProfileJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfileJpaEntity, UUID> {
    Optional<DoctorProfileJpaEntity> findByAccountId(UUID accountId);

    boolean existsByAccountId(UUID accountId);

    @Query("select p from DoctorProfileJpaEntity p where p.inActiveDirectory = true "
            + "and p.acceptingNewBookings = true order by p.fullName, p.id")
    List<DoctorProfileJpaEntity> findAcceptingInActiveDirectory();

    @Query("select p from DoctorProfileJpaEntity p where p.publiclyVisible = true "
            + "and p.inActiveDirectory = true order by p.fullName, p.id")
    List<DoctorProfileJpaEntity> findPublicDirectory();
}
