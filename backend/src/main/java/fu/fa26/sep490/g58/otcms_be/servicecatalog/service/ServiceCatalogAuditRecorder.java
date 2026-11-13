package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServiceCatalogAuditRecorder {
    private final AuditLogWriter auditLog;
    private final Clock clock;

    public ServiceCatalogAuditRecorder(AuditLogWriter auditLog, Clock clock) {
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public void record(UUID serviceId, String serviceName, UUID actorId, String resourceType, UUID resourceId,
            String action, Object before, Object after) {
        String mappedAction = resourceType.equals("DOCTOR_ASSIGNMENT") ? "UPDATE_SERVICE"
                : switch (action) { case "CREATED" -> "CREATE_SERVICE"; case "UPDATED" -> "UPDATE_SERVICE";
                    default -> "CHANGE_SERVICE_STATUS"; };
        auditLog.record(AuditEntry.builder("service_audits", "SERVICE", mappedAction, "SERVICE")
                .entityId(serviceId).recordLabel(serviceName).actorAccountId(actorId)
                .beforeState(before).afterState(after).metadata(Map.of("resourceType", resourceType, "resourceId", resourceId))
                .occurredAt(clock.instant()).build());
    }
}
