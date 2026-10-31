package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.model.Filter;
import fu.fa26.sep490.g58.otcms_be.appointment.model.Row;
import fu.fa26.sep490.g58.otcms_be.appointment.model.Scope;
import fu.fa26.sep490.g58.otcms_be.appointment.model.ScopeType;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentQueryRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AdminAppointmentSearchPage;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AdminAppointmentSummary;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentSearchFilter;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAdminAppointmentsService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetAdminAppointmentsServiceImpl implements GetAdminAppointmentsService {
    private final AppointmentQueryRepository appointments;
    private final PatientProfileDirectory patients;
    private final StaffAvailabilityProviderQueryService doctors;
    private final ClinicServiceBookingQueryService services;

    public GetAdminAppointmentsServiceImpl(AppointmentQueryRepository appointments,
            PatientProfileDirectory patients, StaffAvailabilityProviderQueryService doctors,
            ClinicServiceBookingQueryService services) {
        this.appointments = appointments;
        this.patients = patients;
        this.doctors = doctors;
        this.services = services;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAppointmentSearchPage list(AppointmentSearchFilter filter, int page, int size) {
        var result = appointments.find(new Scope(ScopeType.CLINIC, null),
                new Filter(filter.fromInclusive(), filter.toExclusive(), filter.status(),
                        filter.doctorId(), filter.patientProfileId()), page, size);
        List<AdminAppointmentSummary> items = result.items().stream().map(this::summary).toList();
        return new AdminAppointmentSearchPage(items, result.page(), result.size(),
                result.totalElements(), result.totalPages());
    }

    private AdminAppointmentSummary summary(Row row) {
        var patient = patients.findProfile(row.patientProfileId()).orElse(null);
        var service = services.findServiceLabel(row.serviceId()).orElse(null);
        return new AdminAppointmentSummary(row.id(), row.patientProfileId(),
                patient == null ? "Bệnh nhân" : patient.fullName(),
                patient == null ? null : patient.phoneCanonical(), row.serviceId(),
                service == null ? "Dịch vụ" : service.name(),
                service == null ? null : service.price(), row.doctorId(),
                doctors.findDoctorDisplayName(row.doctorId()).orElse(null), row.status(), row.startTime());
    }
}
