package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.appointment.model.Row;
import fu.fa26.sep490.g58.otcms_be.appointment.model.Filter;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentQueryException;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentSearchFilter;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentSearchPage;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentSummary;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentQueryRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.model.Scope;
import fu.fa26.sep490.g58.otcms_be.appointment.model.ScopeType;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAppointmentsService;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffDirectoryQueryService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class GetAppointmentsServiceImpl implements GetAppointmentsService {
    private final AppointmentQueryRepository queries;
    private final PatientProfileDirectory patients;
    private final AuthorizePatientProfileService patientAuthorization;
    private final StaffDirectoryQueryService staff;
    private final DoctorAccountAccessService doctorAccess;
    private final Clock clock;

    public GetAppointmentsServiceImpl(AppointmentQueryRepository queries, PatientProfileDirectory patients,
            AuthorizePatientProfileService patientAuthorization, StaffDirectoryQueryService staff,
            DoctorAccountAccessService doctorAccess, Clock clock) {
        this.queries = queries; this.patients = patients; this.patientAuthorization = patientAuthorization;
        this.staff = staff; this.doctorAccess = doctorAccess; this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AppointmentSearchPage list(UUID accountId, AccountRole role, AppointmentSearchFilter filter, int page, int size) {
        Scope scope = resolve(accountId, role);
        if (scope == null) return new AppointmentSearchPage(java.util.List.of(), page, size, 0, 0);
        var result = queries.find(scope, new Filter(filter.fromInclusive(), filter.toExclusive(),
                filter.status(), filter.doctorId(), filter.patientProfileId()), page, size);
        return new AppointmentSearchPage(result.items().stream().map(GetAppointmentsServiceImpl::summary).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AppointmentSummary detail(UUID accountId, AccountRole role, UUID id) {
        Scope scope = resolve(accountId, role);
        if (scope == null) throw new AppointmentQueryException("APPOINTMENT_NOT_FOUND");
        var row = queries.findScoped(scope, id).orElseThrow(() -> new AppointmentQueryException("APPOINTMENT_NOT_FOUND"));
        return summary(row);
    }

    private static AppointmentSummary summary(Row row) {
        return new AppointmentSummary(row.id(), row.patientProfileId(), row.serviceId(), row.doctorId(), row.status(),
                row.bookingSource(), row.purpose(), row.startTime(), row.endTime(), row.durationMinutes(),
                row.bufferMinutes(), row.occupiedUntil(), row.visitId(), row.sourceVisitId(), row.createdAt(), row.updatedAt());
    }

    private Scope resolve(UUID accountId, AccountRole role) {
        return switch (role) {
            case RECEPTIONIST -> new Scope(ScopeType.CLINIC, null);
            case PATIENT -> {
                try {
                    yield patients.findPatientProfile(accountId)
                            .map(id -> patientAuthorization.authorize(accountId, id).patientProfileId())
                            .map(id -> new Scope(ScopeType.PATIENT, id)).orElse(null);
                } catch (PatientAuthorizationDeniedException denied) {
                    yield null;
                }
            }
            case DOCTOR -> {
                if (!doctorAccess.isActiveDoctorAccount(accountId)) throw new AppointmentQueryException("ACCESS_DENIED");
                Instant now = clock.instant();
                var profile = staff.findDoctorByAccount(accountId, now, now.plusSeconds(1));
                yield profile == null ? null : new Scope(ScopeType.DOCTOR, profile.doctorProfileId());
            }
            default -> throw new AppointmentQueryException("ACCESS_DENIED");
        };
    }
}
