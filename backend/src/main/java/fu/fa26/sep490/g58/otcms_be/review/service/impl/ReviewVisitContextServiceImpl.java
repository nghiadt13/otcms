package fu.fa26.sep490.g58.otcms_be.review.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentReviewContextService;
import fu.fa26.sep490.g58.otcms_be.review.service.ReviewVisitContextService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService.Source;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Compose public feature projections here, avoiding a new visit -> appointment dependency cycle. */
@Service
public class ReviewVisitContextServiceImpl implements ReviewVisitContextService {
    private final AppointmentReviewContextService appointments;
    private final StaffAvailabilityProviderQueryService doctors;
    public ReviewVisitContextServiceImpl(AppointmentReviewContextService appointments, StaffAvailabilityProviderQueryService doctors) {
        this.appointments = appointments; this.doctors = doctors;
    }
    public Source enrich(Source row) {
        var context = row.originatingAppointmentId() == null ? Optional.<AppointmentReviewContextService.Context>empty()
                : appointments.find(row.originatingAppointmentId());
        String doctor = context.map(AppointmentReviewContextService.Context::doctorName)
                .orElseGet(() -> row.doctorProfileId() == null ? row.doctorName()
                        : doctors.findDoctorDisplayName(row.doctorProfileId()).orElse(""));
        return new Source(row.id(), row.patientProfileId(), row.status(), row.arrivedAt(), doctor,
                context.map(AppointmentReviewContextService.Context::serviceName).orElse(row.serviceName()));
    }
}
