package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.UpdateClinicServiceService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateClinicServiceServiceImpl implements UpdateClinicServiceService {
    private final ClinicServiceRepository services;
    private final CoordinationGuardService coordinationGuard;

    public UpdateClinicServiceServiceImpl(ClinicServiceRepository services, CoordinationGuardService coordinationGuard) {
        this.services = services;
        this.coordinationGuard = coordinationGuard;
    }

    @Override
    @Transactional
    public ClinicService update(UUID serviceId, String name, String description, BigDecimal price,
                                int durationMinutes, int bufferMinutes, boolean onlineBookable) {
        ClinicServiceValidation.ensureId(serviceId);
        String normalizedName = ClinicServiceValidation.normalizeName(name);
        ClinicServiceValidation.validate(normalizedName, price, durationMinutes, bufferMinutes);
        coordinationGuard.acquire();
        ClinicService existing = services.findById(serviceId).map(ClinicServiceMapper::toModel)
                .filter(ClinicService::active)
                .orElseThrow(() -> new ClinicServiceException("SERVICE_NOT_FOUND"));
        ClinicService updated = new ClinicService(existing.id(), normalizedName,
                ClinicServiceValidation.normalizeDescription(description), price, Duration.ofMinutes(durationMinutes),
                Duration.ofMinutes(bufferMinutes), onlineBookable, true);
        return ClinicServiceMapper.toModel(services.save(ClinicServiceMapper.toEntity(updated)));
    }
}
