package fu.fa26.sep490.g58.otcms_be.staff.repository;

import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorOperationalTimeOffJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorOperationalTimeOffRepository
        extends JpaRepository<DoctorOperationalTimeOffJpaEntity, UUID> {
    java.util.Optional<DoctorOperationalTimeOffJpaEntity> findByIdAndDoctorProfileId(UUID id, UUID doctorProfileId);
    boolean existsByDoctorProfileIdAndStartsAtLessThanAndEndsAtGreaterThan(
            UUID doctorProfileId, Instant requestedEnd, Instant requestedStart);

    @Query("select t from DoctorOperationalTimeOffJpaEntity t where t.doctorProfileId in :doctorIds "
            + "and t.startsAt < :rangeEnd and t.endsAt > :rangeStart "
            + "order by t.doctorProfileId, t.startsAt, t.id")
    List<DoctorOperationalTimeOffJpaEntity> findOverlapping(@Param("doctorIds") List<UUID> doctorIds,
                                                            @Param("rangeStart") Instant rangeStart,
                                                            @Param("rangeEnd") Instant rangeEnd);
}
