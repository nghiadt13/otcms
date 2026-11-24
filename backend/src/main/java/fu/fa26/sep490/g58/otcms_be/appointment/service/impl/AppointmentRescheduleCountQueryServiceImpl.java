package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentRescheduleCountQueryService;
import fu.fa26.sep490.g58.otcms_be.common.audit.repository.AuditLogRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentRescheduleCountQueryServiceImpl implements AppointmentRescheduleCountQueryService {
    private static final String SOURCE = "appointment_audits";
    private static final String ACTION = "RESCHEDULED";
    private static final String PATIENT_ACTOR_TYPE = "PATIENT";

    private final AuditLogRepository auditLogs;

    public AppointmentRescheduleCountQueryServiceImpl(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    @Transactional(readOnly = true)
    public int countPatientReschedules(UUID appointmentId) {
        if (appointmentId == null) return 0;
        long count = auditLogs.countBySourceAndActionAndEntityIdAndActorType(
                SOURCE, ACTION, appointmentId, PATIENT_ACTOR_TYPE);
        return Math.toIntExact(count);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> countPatientReschedules(Collection<UUID> appointmentIds) {
        if (appointmentIds == null || appointmentIds.isEmpty()) return Map.of();
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : auditLogs.countByEntityIdsAndSourceAndActionAndActorType(
                appointmentIds, SOURCE, ACTION, PATIENT_ACTOR_TYPE)) {
            if (row[0] instanceof UUID id && row[1] instanceof Number number) {
                counts.put(id, number.intValue());
            }
        }
        return Map.copyOf(counts);
    }
}
