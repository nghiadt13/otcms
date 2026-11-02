package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.CreateClinicServiceService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.List;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateClinicServiceServiceImpl implements CreateClinicServiceService {
    private final ClinicServiceRepository services;
    private final CoordinationGuardService coordinationGuard;

    public CreateClinicServiceServiceImpl(ClinicServiceRepository services, CoordinationGuardService coordinationGuard) {
        this.services = services;
        this.coordinationGuard = coordinationGuard;
    }

    @Override
    @Transactional
    public ClinicService create(String name, String description, BigDecimal price,
                                int durationMinutes, int bufferMinutes, boolean onlineBookable) {
        return create(name, description, ClinicServiceType.EXAMINATION, price, durationMinutes, bufferMinutes,
                onlineBookable);
    }

    @Override
    @Transactional
    public ClinicService create(String name, String description, ClinicServiceType type, BigDecimal price,
                                int durationMinutes, int bufferMinutes, boolean onlineBookable) {
        return create(name, description, type, price, durationMinutes, bufferMinutes, onlineBookable,
                null, null, List.of());
    }

    @Override
    @Transactional
    public ClinicService create(String name, String description, ClinicServiceType type, BigDecimal price,
                                int durationMinutes, int bufferMinutes, boolean onlineBookable, String modality,
                                String patientNote, List<LabIndicator> indicators) {
        String normalizedName = ClinicServiceValidation.normalizeName(name);
        ClinicServiceValidation.validate(normalizedName, price, durationMinutes, bufferMinutes);
        ClinicServiceValidation.validateCatalogDetails(type, modality, indicators);
        ClinicServiceType resolvedType = type == null ? ClinicServiceType.EXAMINATION : type;
        coordinationGuard.acquire();
        if (services.existsByNameIgnoreCaseAndActiveTrue(normalizedName))
            throw new fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException("DUPLICATE_SERVICE_NAME");
        ClinicService service = new ClinicService(UUID.randomUUID(), normalizedName,
                ClinicServiceValidation.normalizeDescription(description),
                resolvedType, price, Duration.ofMinutes(durationMinutes),
                Duration.ofMinutes(bufferMinutes), onlineBookable, true, modality,
                ClinicServiceValidation.normalizeDescription(patientNote), indicators);
        return ClinicServiceMapper.toModel(services.save(ClinicServiceMapper.toEntity(service)));
    }
}
