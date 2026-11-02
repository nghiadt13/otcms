package fu.fa26.sep490.g58.otcms_be.staff.repository;

import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorWorkShiftJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorWorkShiftRepository extends JpaRepository<DoctorWorkShiftJpaEntity, UUID> {
    List<DoctorWorkShiftJpaEntity> findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(UUID doctorProfileId);
    java.util.Optional<DoctorWorkShiftJpaEntity> findByIdAndDoctorProfileId(UUID id, UUID doctorProfileId);
    boolean existsByDoctorProfileIdAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
            UUID doctorProfileId, Instant startsAt, Instant endsAt);

    @Query("select s from DoctorWorkShiftJpaEntity s where s.doctorProfileId = :doctorId "
            + "and s.startsAt < :rangeEnd and s.endsAt > :rangeStart order by s.startsAt, s.id")
    List<DoctorWorkShiftJpaEntity> findInRange(@Param("doctorId") UUID doctorId,
                                               @Param("rangeStart") Instant rangeStart,
                                               @Param("rangeEnd") Instant rangeEnd);

    @Query("select s from DoctorWorkShiftJpaEntity s where s.doctorProfileId in :doctorIds "
            + "and s.startsAt < :rangeEnd and s.endsAt > :rangeStart "
            + "order by s.doctorProfileId, s.startsAt, s.id")
    List<DoctorWorkShiftJpaEntity> findOverlapping(@Param("doctorIds") List<UUID> doctorIds,
                                                   @Param("rangeStart") Instant rangeStart,
                                                   @Param("rangeEnd") Instant rangeEnd);
}
