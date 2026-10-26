package fu.fa26.sep490.g58.otcms_be.servicecatalog.repository;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.ServiceCatalogManagementAuditJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface ServiceCatalogManagementAuditRepository extends JpaRepository<ServiceCatalogManagementAuditJpaEntity, UUID> {
    @Modifying
    @Query(value = "insert into service_audits "
            + "(id,service_id,actor_account_id,resource_type,resource_id,action,before_state,after_state,occurred_at,correlation_id) "
            + "values (:id,:serviceId,:actorId,:type,:resourceId,:action,cast(:beforeState as jsonb),cast(:afterState as jsonb),:occurredAt,:correlationId)", nativeQuery = true)
    int append(@Param("id") UUID id, @Param("serviceId") UUID serviceId, @Param("actorId") UUID actorId,
            @Param("type") String type, @Param("resourceId") UUID resourceId, @Param("action") String action,
            @Param("beforeState") String beforeState, @Param("afterState") String afterState,
            @Param("occurredAt") Instant occurredAt, @Param("correlationId") String correlationId);
}
