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
import java.util.List;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator;
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
        return updateInternal(serviceId, name, description, price, durationMinutes, bufferMinutes,
                onlineBookable, null, null, null, false);
    }

    @Override
    @Transactional
    public ClinicService update(UUID serviceId, String name, String description, BigDecimal price,
                                int durationMinutes, int bufferMinutes, String modality, String patientNote,
                                List<LabIndicator> indicators) {
        return updateInternal(serviceId, name, description, price, durationMinutes, bufferMinutes,
                false, modality, patientNote, indicators, true);
    }

    private ClinicService updateInternal(UUID serviceId, String name, String description, BigDecimal price,
                                int durationMinutes, int bufferMinutes, boolean onlineBookable, String modality,
                                String patientNote, List<LabIndicator> indicators, boolean updateCatalogDetails) {
        ClinicServiceValidation.ensureId(serviceId);
        String normalizedName = ClinicServiceValidation.normalizeName(name);
        ClinicServiceValidation.validate(normalizedName, price, durationMinutes, bufferMinutes);
        coordinationGuard.acquire();
        ClinicService existing = services.findById(serviceId).map(ClinicServiceMapper::toModel)
                .filter(ClinicService::active)
                .orElseThrow(() -> new ClinicServiceException("SERVICE_NOT_FOUND"));
        if (services.existsByNameIgnoreCaseAndActiveTrueAndIdNot(normalizedName, serviceId))
            throw new ClinicServiceException("DUPLICATE_SERVICE_NAME");
        ClinicServiceValidation.validateCatalogDetails(existing.type(),
                updateCatalogDetails ? modality : existing.modality(),
                updateCatalogDetails ? indicators : existing.indicators());
        int resolvedDuration = updateCatalogDetails && existing.type() != fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType.EXAMINATION
                ? Math.toIntExact(existing.consultationDuration().toMinutes()) : durationMinutes;
        int resolvedBuffer = updateCatalogDetails && existing.type() != fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType.EXAMINATION
                ? Math.toIntExact(existing.bufferDuration().toMinutes()) : bufferMinutes;
        ClinicService updated = new ClinicService(existing.id(), normalizedName,
                ClinicServiceValidation.normalizeDescription(description), existing.type(), price,
                Duration.ofMinutes(resolvedDuration),
                Duration.ofMinutes(resolvedBuffer), updateCatalogDetails ? existing.onlineBookable() : onlineBookable,
                true, updateCatalogDetails ? modality : existing.modality(),
                updateCatalogDetails ? ClinicServiceValidation.normalizeDescription(patientNote) : existing.patientNote(),
                updateCatalogDetails ? indicators : existing.indicators());
        return ClinicServiceMapper.toModel(services.save(ClinicServiceMapper.toEntity(updated)));
    }
}
