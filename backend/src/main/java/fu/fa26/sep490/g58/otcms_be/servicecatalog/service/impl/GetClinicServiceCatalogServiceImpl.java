package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import java.util.List;
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
    public List<ClinicService> adminCatalog() {
        return services.findAll(org.springframework.data.domain.Sort.by("name", "id")).stream()
                .map(ClinicServiceMapper::toModel).toList();
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
}
