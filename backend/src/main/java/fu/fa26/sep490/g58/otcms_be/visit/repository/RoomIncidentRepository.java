package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.RoomIncidentJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomIncidentRepository extends JpaRepository<RoomIncidentJpaEntity, UUID> {
    List<RoomIncidentJpaEntity> findAllByStatusOrderByReportedAtAscIdAsc(String status, Pageable pageable);
    Optional<RoomIncidentJpaEntity> findFirstByReportedByAccountIdAndReportIdempotencyKey(UUID accountId, String key);
    boolean existsByRoomIdAndStatusAndIdNot(UUID roomId, String status, UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select incident from RoomIncidentJpaEntity incident where incident.id = :id")
    Optional<RoomIncidentJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
