package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ServiceCatalogManagementAuditRepository;
import java.time.Clock;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.DeactivateClinicServiceService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeactivateClinicServiceServiceImpl implements DeactivateClinicServiceService {
    private final ClinicServiceRepository services;
    private final CoordinationGuardService coordinationGuard;
    private final ServiceCatalogManagementAuditRepository audits;
    private final Clock clock;

    public DeactivateClinicServiceServiceImpl(ClinicServiceRepository services, CoordinationGuardService coordinationGuard,
            ServiceCatalogManagementAuditRepository audits, Clock clock) {
        this.services = services;
        this.coordinationGuard = coordinationGuard;
        this.audits = audits;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void deactivate(UUID serviceId, AuthenticatedAccount actor, String correlationId) {
        ClinicServiceValidation.ensureId(serviceId);
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.ADMIN)
            throw new ClinicServiceException("ACCESS_DENIED");
        if (correlationId == null || correlationId.isBlank())
            throw new ClinicServiceException("SERVICE_REQUEST_INVALID");
        coordinationGuard.acquire();
        var service = services.findById(serviceId).orElseThrow(() -> new ClinicServiceException("SERVICE_NOT_FOUND"));
        if (service.active()) {
            service.deactivate();
            services.save(service);
            audits.append(UUID.randomUUID(), serviceId, actor.accountId(), "CLINIC_SERVICE", serviceId,
                    "DEACTIVATED", "{\"active\":true}", "{\"active\":false}", clock.instant(), correlationId);
        }
    }
}
