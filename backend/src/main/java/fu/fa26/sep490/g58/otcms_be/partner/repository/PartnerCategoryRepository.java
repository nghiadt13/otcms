package fu.fa26.sep490.g58.otcms_be.partner.repository;

import fu.fa26.sep490.g58.otcms_be.partner.model.entity.PartnerCategoryJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface PartnerCategoryRepository extends JpaRepository<PartnerCategoryJpaEntity, UUID> {
    @Query("select c from PartnerCategoryJpaEntity c where c.code in :codes and c.active = true")
    List<PartnerCategoryJpaEntity> findActiveByCodes(@Param("codes") Collection<String> codes);

    @Query("select c from PartnerCategoryJpaEntity c where c.code = :code and c.active = true")
    Optional<PartnerCategoryJpaEntity> findActiveByCode(@Param("code") String code);
}
