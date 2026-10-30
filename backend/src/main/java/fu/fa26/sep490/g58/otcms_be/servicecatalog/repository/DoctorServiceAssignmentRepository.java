package fu.fa26.sep490.g58.otcms_be.servicecatalog.repository;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.DoctorServiceAssignmentJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorServiceAssignmentRepository extends JpaRepository<DoctorServiceAssignmentJpaEntity, UUID> {
    java.util.Optional<DoctorServiceAssignmentJpaEntity> findByIdAndServiceIdAndActiveTrue(UUID id, UUID serviceId);
    @Modifying
    @Query("update DoctorServiceAssignmentJpaEntity a set a.active = false where a.id = :id and a.active = true")
    int deactivate(@Param("id") UUID id);
    boolean existsByDoctorProfileIdAndServiceIdAndActiveTrue(UUID doctorProfileId, UUID serviceId);

    List<DoctorServiceAssignmentJpaEntity> findByServiceIdAndActiveTrueOrderByDoctorProfileIdAsc(UUID serviceId);
}
