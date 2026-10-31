package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetClinicServiceCatalogServiceImpl implements GetClinicServiceCatalogService {
    private final ClinicServiceRepository services;

    public GetClinicServiceCatalogServiceImpl(ClinicServiceRepository services) {
        this.services = services;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClinicService> publicCatalog() {
        return services.findByActiveTrueAndOnlineBookableTrueOrderByNameAscIdAsc().stream()
                .map(ClinicServiceMapper::toModel).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClinicService> staffCatalog() {
        return services.findByActiveTrueOrderByNameAscIdAsc().stream().map(ClinicServiceMapper::toModel).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClinicService> roomOptions(ClinicServiceType type, boolean includeInactive) {
        var rows = includeInactive ? services.findAll() : services.findByActiveTrueOrderByNameAscIdAsc();
        return rows.stream()
                .map(ClinicServiceMapper::toModel)
                .filter(service -> type == null || service.type() == type)
                .sorted(Comparator.comparing(ClinicService::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ClinicService::id))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClinicService> adminCatalog() {
        return services.findAll().stream().map(ClinicServiceMapper::toModel)
                .sorted(Comparator.comparing(ClinicService::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ClinicService::id)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicService getService(UUID serviceId) {
        if (serviceId == null) throw new ClinicServiceException("SERVICE_NOT_FOUND");
        return services.findById(serviceId).map(ClinicServiceMapper::toModel)
                .orElseThrow(() -> new ClinicServiceException("SERVICE_NOT_FOUND"));
    }
}
