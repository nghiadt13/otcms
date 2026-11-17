package fu.fa26.sep490.g58.otcms_be.partner.repository;

import fu.fa26.sep490.g58.otcms_be.partner.model.entity.PartnerJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerRepository extends JpaRepository<PartnerJpaEntity, UUID> {
    @Query("select distinct p from PartnerJpaEntity p left join p.categories c " +
            "where (:activeOnly = false or p.active = true) and (:categoryCode is null or c.code = :categoryCode) " +
            "order by p.name, p.id")
    List<PartnerJpaEntity> list(@Param("activeOnly") boolean activeOnly, @Param("categoryCode") String categoryCode);
    @Query("select distinct p from PartnerJpaEntity p join p.categories c where p.id = :id and p.active = true and c.code = :categoryCode and c.active = true")
    java.util.Optional<PartnerJpaEntity> activeForCategory(@Param("id") UUID id, @Param("categoryCode") String categoryCode);
}
