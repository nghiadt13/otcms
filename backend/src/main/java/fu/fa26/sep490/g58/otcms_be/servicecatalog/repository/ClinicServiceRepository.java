package fu.fa26.sep490.g58.otcms_be.servicecatalog.repository;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.ClinicServiceJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicServiceRepository extends JpaRepository<ClinicServiceJpaEntity, UUID> {
    boolean existsByNameIgnoreCaseAndActiveTrue(String name);
    boolean existsByNameIgnoreCaseAndActiveTrueAndIdNot(String name, UUID id);
    @Modifying
    @Query("update ClinicServiceJpaEntity s set s.active = false where s.id = :serviceId and s.active = true")
    int deactivate(@Param("serviceId") UUID serviceId);
    List<ClinicServiceJpaEntity> findByActiveTrueOrderByNameAscIdAsc();

    List<ClinicServiceJpaEntity> findByActiveTrueAndOnlineBookableTrueOrderByNameAscIdAsc();
}
