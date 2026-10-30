package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.CreateClinicServiceService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
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
        String normalizedName = ClinicServiceValidation.normalizeName(name);
        ClinicServiceValidation.validate(normalizedName, price, durationMinutes, bufferMinutes);
        coordinationGuard.acquire();
        ClinicService service = new ClinicService(UUID.randomUUID(), normalizedName,
                ClinicServiceValidation.normalizeDescription(description), price, Duration.ofMinutes(durationMinutes),
                Duration.ofMinutes(bufferMinutes), onlineBookable, true);
        return ClinicServiceMapper.toModel(services.save(ClinicServiceMapper.toEntity(service)));
    }
}
