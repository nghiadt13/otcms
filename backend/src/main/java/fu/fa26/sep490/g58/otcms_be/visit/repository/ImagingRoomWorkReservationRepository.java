package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ImagingRoomWorkReservationJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImagingRoomWorkReservationRepository extends JpaRepository<ImagingRoomWorkReservationJpaEntity, UUID> {
    boolean existsByRequestIdAndState(UUID requestId, String state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from ImagingRoomWorkReservationJpaEntity reservation where reservation.requestId = :requestId")
    Optional<ImagingRoomWorkReservationJpaEntity> findByRequestIdForUpdate(@Param("requestId") UUID requestId);
}
