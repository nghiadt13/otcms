package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicVisitJpaRepository extends JpaRepository<ClinicVisitJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select visit from ClinicVisitJpaEntity visit where visit.id = :visitId")
    Optional<ClinicVisitJpaEntity> findByIdForUpdate(@Param("visitId") UUID visitId);
}
