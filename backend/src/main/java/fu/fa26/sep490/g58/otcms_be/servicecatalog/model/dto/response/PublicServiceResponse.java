package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Public catalog projection. Laboratory reference ranges are intentionally internal. */
public record PublicServiceResponse(UUID id, String name, String description, ClinicServiceType type,
        BigDecimal price, int durationMinutes, int bufferMinutes, boolean onlineBookable,
        boolean active, String modality, String patientNote) {
    public static PublicServiceResponse from(ClinicService service) {
        return new PublicServiceResponse(service.id(), service.name(), service.description(), service.type(),
                service.price(), Math.toIntExact(service.consultationDuration().toMinutes()),
                Math.toIntExact(service.bufferDuration().toMinutes()), service.onlineBookable(),
                service.active(), service.modality(), service.patientNote());
    }
    public record Catalog(List<PublicServiceResponse> items) { }
}
