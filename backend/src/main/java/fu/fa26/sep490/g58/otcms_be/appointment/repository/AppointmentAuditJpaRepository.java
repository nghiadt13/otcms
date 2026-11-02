package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentAuditJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AppointmentAuditJpaRepository extends JpaRepository<AppointmentAuditJpaEntity, UUID> { }
