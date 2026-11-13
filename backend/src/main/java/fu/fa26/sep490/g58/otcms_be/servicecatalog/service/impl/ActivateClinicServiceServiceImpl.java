package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ActivateClinicServiceService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceCatalogAuditRecorder;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivateClinicServiceServiceImpl implements ActivateClinicServiceService {
    private final ClinicServiceRepository services;
    private final CoordinationGuardService coordinationGuard;
    private final ServiceCatalogAuditRecorder audits;

    public ActivateClinicServiceServiceImpl(ClinicServiceRepository services, CoordinationGuardService coordinationGuard,
            ServiceCatalogAuditRecorder audits) {
        this.services = services;
        this.coordinationGuard = coordinationGuard;
        this.audits = audits;
    }

    @Override
    @Transactional
    public void activate(UUID serviceId, AuthenticatedAccount actor, String correlationId) {
        ClinicServiceValidation.ensureId(serviceId);
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.ADMIN)
            throw new ClinicServiceException("ACCESS_DENIED");
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > 128)
            throw new ClinicServiceException("SERVICE_REQUEST_INVALID");
        coordinationGuard.acquire();
        var existing = services.findById(serviceId)
                .orElseThrow(() -> new ClinicServiceException("SERVICE_NOT_FOUND"));
        if (!existing.active()) {
            existing.activate();
            services.save(existing);
            audits.record(serviceId, existing.name(), actor.accountId(), "CLINIC_SERVICE", serviceId,
                    "UPDATED", Map.of("active", false), Map.of("active", true));
        }
    }
}
