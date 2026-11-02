package fu.fa26.sep490.g58.otcms_be.staff.repository;

import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffResourceManagementAuditJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface StaffResourceManagementAuditRepository
        extends JpaRepository<StaffResourceManagementAuditJpaEntity, UUID> {
    @Modifying
    @Query(value = "insert into staff_resource_audits "
            + "(id,doctor_profile_id,actor_account_id,resource_type,resource_id,action,before_state,after_state,occurred_at,correlation_id) "
            + "values (:id,:doctorId,:actorId,:type,:resourceId,:action,cast(:beforeState as jsonb),cast(:afterState as jsonb),:occurredAt,:correlationId)", nativeQuery = true)
    int append(@Param("id") UUID id, @Param("doctorId") UUID doctorId, @Param("actorId") UUID actorId,
            @Param("type") String type, @Param("resourceId") UUID resourceId, @Param("action") String action,
            @Param("beforeState") String beforeState, @Param("afterState") String afterState,
            @Param("occurredAt") Instant occurredAt, @Param("correlationId") String correlationId);
}
