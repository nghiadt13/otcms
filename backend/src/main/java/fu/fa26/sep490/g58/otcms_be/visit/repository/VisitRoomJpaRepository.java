package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitRoomJpaRepository extends JpaRepository<VisitRoomJpaEntity, UUID> {
    interface ImagingQueueRoomRow {
        boolean getActive();
        String getType();
        boolean getImagingCapability();
        UUID getServiceId();
        UUID getRoomId();
        String getRoomCode();
        String getDisplayLabel();
    }

    @Query(value = """
            select s.service_id as "serviceId", r.id as "roomId", r.room_code as "roomCode", r.display_label as "displayLabel",
                r.active as active, r.room_type as type,
                exists (select 1 from room_capabilities c where c.room_id = r.id and c.capability_code = 'IMAGING_ROOM') as "imagingCapability"
            from rooms r join room_supported_services s on s.room_id = r.id
            where s.service_id in (:serviceIds)
            order by r.room_code, r.id
            """, nativeQuery = true)
    List<ImagingQueueRoomRow> findImagingQueueRooms(@Param("serviceIds") java.util.Collection<UUID> serviceIds);

    boolean existsByRoomCode(String roomCode);

    List<VisitRoomJpaEntity> findAllByOrderByRoomCodeAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from VisitRoomJpaEntity room where room.id = :roomId")
    Optional<VisitRoomJpaEntity> findByIdForUpdate(@Param("roomId") UUID roomId);

    @Query(value = """
            select exists(
                select 1 from room_reservations
                where room_id = :roomId
                  and (status = 'IN_PROGRESS' or (status = 'CLEANING' and reserved_until > :now))
            )
            """, nativeQuery = true)
    boolean hasBlockingUse(@Param("roomId") UUID roomId, @Param("now") Instant now);

    @Query(value = """
            select jsonb_build_object(
                'roomCode', r.room_code,
                'displayLabel', r.display_label,
                'type', r.room_type,
                'description', r.description,
                'active', r.active,
                'capabilities', coalesce((
                    select jsonb_agg(c.capability_code order by c.capability_code)
                    from room_capabilities c where c.room_id = r.id
                ), '[]'::jsonb),
                'supportedServiceIds', coalesce((
                    select jsonb_agg(s.service_id order by s.service_id)
                    from room_supported_services s where s.room_id = r.id
                ), '[]'::jsonb)
            )::text
            from rooms r where r.id = :roomId
            """, nativeQuery = true)
    String snapshot(@Param("roomId") UUID roomId);

    @Modifying
    @Query(value = """
            insert into room_management_audits
                (id, room_id, actor_account_id, action, before_state, after_state, occurred_at, correlation_id)
            values (:id, :roomId, :actorId, :action, cast(:beforeState as jsonb),
                cast(:afterState as jsonb), :occurredAt, :correlationId)
            """, nativeQuery = true)
    int appendManagementAudit(@Param("id") UUID id, @Param("roomId") UUID roomId,
            @Param("actorId") UUID actorId, @Param("action") String action,
            @Param("beforeState") String beforeState, @Param("afterState") String afterState,
            @Param("occurredAt") Instant occurredAt, @Param("correlationId") String correlationId);
}
