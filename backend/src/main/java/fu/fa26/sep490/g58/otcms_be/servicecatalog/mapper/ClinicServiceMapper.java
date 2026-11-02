package fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response.ItemsResponse;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response.ServiceResponse;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.ClinicServiceJpaEntity;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ClinicServiceMapper {
    public static ClinicService toModel(ClinicServiceJpaEntity entity) {
        return new ClinicService(entity.id(), entity.name(), entity.description(),
                entity.type(), entity.price(),
                Duration.ofMinutes(entity.durationMinutes()), Duration.ofMinutes(entity.bufferMinutes()),
                entity.onlineBookable(), entity.active(), entity.modality(), entity.patientNote(), entity.indicators());
    }

    public static ClinicServiceJpaEntity toEntity(ClinicService service) {
        ClinicServiceJpaEntity entity = new ClinicServiceJpaEntity(service.id(), service.name(), service.description(), service.type(), service.price(),
                Math.toIntExact(service.consultationDuration().toMinutes()),
                Math.toIntExact(service.bufferDuration().toMinutes()), service.onlineBookable(), service.active());
        entity.updateCatalogDetails(service.modality(), service.patientNote(), service.indicators());
        return entity;
    }

    public ServiceResponse toResponse(ClinicService service) {
        return new ServiceResponse(service.id(), service.name(), service.description(), service.type(), service.price(),
                Math.toIntExact(service.consultationDuration().toMinutes()),
                Math.toIntExact(service.bufferDuration().toMinutes()), service.onlineBookable(), service.active(),
                service.modality(), service.patientNote(), service.indicators());
    }
    public ItemsResponse toItemsResponse(List<ClinicService> services) {
        return new ItemsResponse(services.stream().map(this::toResponse).toList());
    }
}
