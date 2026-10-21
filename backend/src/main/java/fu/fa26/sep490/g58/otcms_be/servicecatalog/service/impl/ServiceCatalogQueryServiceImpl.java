package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService.BookableService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService.BookingAudience;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceDoctorAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.DoctorServiceAssignmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServiceCatalogQueryServiceImpl implements ClinicServiceBookingQueryService, ServiceDoctorAssignmentQueryService {
    private final ClinicServiceRepository services;
    private final DoctorServiceAssignmentRepository assignments;

    public ServiceCatalogQueryServiceImpl(ClinicServiceRepository services,
                                 DoctorServiceAssignmentRepository assignments) {
        this.services = services;
        this.assignments = assignments;
    }

    @Override
    public BookableService findActiveService(UUID serviceId, BookingAudience audience) {
        var service = services.findById(serviceId).map(ClinicServiceMapper::toModel)
                .filter(value -> value.active())
                .filter(value -> audience != BookingAudience.PUBLIC_PATIENT || value.onlineBookable())
                .orElseThrow(() -> new ClinicServiceException("SERVICE_NOT_BOOKABLE"));
        return new BookableService(service.id(), service.name(), service.consultationDuration(),
                service.bufferDuration(), service.onlineBookable());
    }

    @Override
    public boolean hasActiveDoctorAssignment(UUID serviceId, UUID doctorProfileId) {
        return assignments.existsByDoctorProfileIdAndServiceIdAndActiveTrue(doctorProfileId, serviceId);
    }

    @Override
    public List<UUID> findActiveDoctorProfileIds(UUID serviceId) {
        if (serviceId == null) {
            throw new IllegalArgumentException("Service ID is required");
        }
        return assignments.findByServiceIdAndActiveTrueOrderByDoctorProfileIdAsc(serviceId).stream()
                .map(assignment -> assignment.doctorProfileId()).toList();
    }
}
