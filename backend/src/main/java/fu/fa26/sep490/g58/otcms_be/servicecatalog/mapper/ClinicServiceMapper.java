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
                Duration.ofMinutes(entity.durationMinutes()), Duration.ofMinutes(entity.bufferMinutes()),
                entity.onlineBookable(), entity.active());
    }

    public static ClinicServiceJpaEntity toEntity(ClinicService service) {
        return new ClinicServiceJpaEntity(service.id(), service.name(), service.description(),
                Math.toIntExact(service.consultationDuration().toMinutes()),
                Math.toIntExact(service.bufferDuration().toMinutes()), service.onlineBookable(), service.active());
    }

    public ServiceResponse toResponse(ClinicService service) {
        return new ServiceResponse(service.id(), service.name(), service.description(),
                Math.toIntExact(service.consultationDuration().toMinutes()),
                Math.toIntExact(service.bufferDuration().toMinutes()), service.onlineBookable(), service.active());
    }
    public ItemsResponse toItemsResponse(List<ClinicService> services) {
        return new ItemsResponse(services.stream().map(this::toResponse).toList());
    }
}
