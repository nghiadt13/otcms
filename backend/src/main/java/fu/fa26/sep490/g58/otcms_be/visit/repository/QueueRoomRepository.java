package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueRoomRepository extends JpaRepository<QueueEntryJpaEntity, UUID> {
    interface Location {
        UUID getRoomId();
        String getStage();
    }

    @Query("select q.roomId as roomId, q.stage as stage from QueueEntryJpaEntity q where q.id = :id")
    java.util.Optional<Location> findLocation(@Param("id") UUID id);

    @Query("select q from QueueEntryJpaEntity q where q.visitId = :visitId and q.stage = 'SERVICE' order by q.enteredAt desc")
    List<QueueEntryJpaEntity> findServicesForVisit(@Param("visitId") UUID visitId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QueueEntryJpaEntity q where q.roomId = :roomId and q.stage = :stage order by q.sortOrder, q.enteredAt, q.id")
    List<QueueEntryJpaEntity> lockRoom(@Param("roomId") UUID roomId, @Param("stage") String stage);

    @Query("select q from QueueEntryJpaEntity q where q.roomId = :roomId and q.stage = :stage order by q.sortOrder, q.enteredAt, q.id")
    List<QueueEntryJpaEntity> findRoom(@Param("roomId") UUID roomId, @Param("stage") String stage);
}
