package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentQueryException;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentQueryRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentBillingSourceService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentBillingSourceServiceImpl implements AppointmentBillingSourceService {
    private final AppointmentQueryRepository appointments;
    private final ClinicServiceBookingQueryService services;

    public AppointmentBillingSourceServiceImpl(AppointmentQueryRepository appointments,
            ClinicServiceBookingQueryService services) {
        this.appointments = appointments;
        this.services = services;
    }

    @Override
    @Transactional(readOnly = true)
    public BillingSource requireSource(UUID appointmentId) {
        if (appointmentId == null) throw new AppointmentQueryException("APPOINTMENT_NOT_FOUND");
        var appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new AppointmentQueryException("APPOINTMENT_NOT_FOUND"));
        var service = services.findServiceLabel(appointment.getServiceId())
                .orElseThrow(() -> new AppointmentQueryException("APPOINTMENT_NOT_FOUND"));
        return new BillingSource(appointment.getId(), appointment.getPatientProfileId(), appointment.getServiceId(),
                appointment.getStatus(), service.name(), service.price());
    }
}
