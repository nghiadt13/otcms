package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentJpaRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentReviewContextService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class AppointmentReviewContextServiceImpl implements AppointmentReviewContextService {
    private final AppointmentJpaRepository appointments;
    private final ClinicServiceBookingQueryService services;
    private final StaffAvailabilityProviderQueryService doctors;
    public AppointmentReviewContextServiceImpl(AppointmentJpaRepository appointments,
            ClinicServiceBookingQueryService services, StaffAvailabilityProviderQueryService doctors) {
        this.appointments = appointments; this.services = services; this.doctors = doctors;
    }
    public Optional<Context> find(UUID id) {
        return appointments.findById(id).map(a -> new Context(doctors.findDoctorDisplayName(a.getDoctorProfileId()).orElse(""),
                services.findServiceLabel(a.getServiceId()).map(ClinicServiceBookingQueryService.ServiceLabel::name).orElse("")));
    }
}
