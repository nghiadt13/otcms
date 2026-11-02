package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;
import fu.fa26.sep490.g58.otcms_be.common.exception.ResourceMutationBlockedException;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response.DoctorAssignmentResponse;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.DoctorServiceAssignmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.DoctorServiceAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ServiceCatalogManagementAuditRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ManageDoctorServiceAssignmentsService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageDoctorServiceAssignmentsServiceImpl implements ManageDoctorServiceAssignmentsService {
    private final ClinicServiceRepository services;
    private final DoctorServiceAssignmentRepository assignments;
    private final ServiceCatalogManagementAuditRepository audits;
    private final DoctorProfileQueryService doctors;
    private final AppointmentMutationImpactService appointmentImpact;
    private final VisitMutationImpactService visitImpact;
    private final CoordinationGuardService guard;
    private final Clock clock;
    public ManageDoctorServiceAssignmentsServiceImpl(ClinicServiceRepository services,
            DoctorServiceAssignmentRepository assignments, ServiceCatalogManagementAuditRepository audits,
            DoctorProfileQueryService doctors, AppointmentMutationImpactService appointmentImpact,
            VisitMutationImpactService visitImpact, CoordinationGuardService guard, Clock clock) {
        this.services=services; this.assignments=assignments; this.audits=audits; this.doctors=doctors;
        this.appointmentImpact=appointmentImpact; this.visitImpact=visitImpact; this.guard=guard; this.clock=clock;
    }
    @Override @Transactional(readOnly = true)
    public List<DoctorAssignmentResponse> list(UUID serviceId, AuthenticatedAccount actor) {
        if (serviceId == null || actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.ADMIN)
            throw new ClinicServiceException("ACCESS_DENIED");
        services.findById(serviceId).orElseThrow(() -> new ClinicServiceException("SERVICE_NOT_FOUND"));
        return assignments.findByServiceIdAndActiveTrueOrderByDoctorProfileIdAsc(serviceId).stream()
                .map(assignment -> new DoctorAssignmentResponse(assignment.id(), serviceId,
                        assignment.doctorProfileId()))
                .toList();
    }
    @Override @Transactional
    public DoctorAssignmentResponse assign(UUID serviceId, UUID doctorId, AuthenticatedAccount actor, String correlationId) {
        validate(serviceId, actor, correlationId); if (doctorId == null) throw invalid();
        guard.acquire();
        var service=services.findById(serviceId).filter(s -> s.active()).orElseThrow(() -> new ClinicServiceException("SERVICE_NOT_FOUND"));
        if (!doctors.existsDoctorProfile(doctorId)) throw new ClinicServiceException("DOCTOR_PROFILE_NOT_FOUND");
        if (assignments.existsByDoctorProfileIdAndServiceIdAndActiveTrue(doctorId, serviceId))
            throw new ClinicServiceException("DOCTOR_ASSIGNMENT_ALREADY_EXISTS");
        UUID assignmentId=UUID.randomUUID();
        assignments.save(new DoctorServiceAssignmentJpaEntity(assignmentId, doctorId, serviceId, true));
        audits.append(UUID.randomUUID(), serviceId, actor.accountId(), "DOCTOR_ASSIGNMENT", assignmentId, "CREATED", null,
                "{\"doctorProfileId\":\""+doctorId+"\",\"active\":true}", clock.instant(), correlationId);
        return new DoctorAssignmentResponse(assignmentId, service.id(), doctorId);
    }
    @Override @Transactional
    public void remove(UUID serviceId, UUID assignmentId, AuthenticatedAccount actor, String correlationId) {
        validate(serviceId, actor, correlationId); if (assignmentId == null) throw invalid();
        guard.acquire();
        services.findById(serviceId).orElseThrow(() -> new ClinicServiceException("SERVICE_NOT_FOUND"));
        var assignment=assignments.findByIdAndServiceIdAndActiveTrue(assignmentId, serviceId)
                .orElseThrow(() -> new ClinicServiceException("DOCTOR_ASSIGNMENT_NOT_FOUND"));
        AdminMutationImpact impact = assignmentImpact(assignment, serviceId, assignmentId);
        if (impact.isBlocked()) throw new ResourceMutationBlockedException(impact);
        assignment.deactivate();
        assignments.save(assignment);
        audits.append(UUID.randomUUID(), serviceId, actor.accountId(), "DOCTOR_ASSIGNMENT", assignmentId, "DELETED",
                "{\"doctorProfileId\":\""+assignment.doctorProfileId()+"\",\"active\":true}",
                "{\"active\":false}", clock.instant(), correlationId);
    }
    @Override @Transactional
    public AdminMutationImpact assessRemovalImpact(UUID serviceId, UUID assignmentId, AuthenticatedAccount actor) {
        if (serviceId == null || assignmentId == null || actor == null || actor.accountId() == null
                || actor.accountRole() != AccountRole.ADMIN) throw new ClinicServiceException("ACCESS_DENIED");
        guard.acquire();
        services.findById(serviceId).orElseThrow(() -> new ClinicServiceException("SERVICE_NOT_FOUND"));
        var assignment=assignments.findByIdAndServiceIdAndActiveTrue(assignmentId, serviceId)
                .orElseThrow(() -> new ClinicServiceException("DOCTOR_ASSIGNMENT_NOT_FOUND"));
        return assignmentImpact(assignment, serviceId, assignmentId);
    }

    private AdminMutationImpact assignmentImpact(DoctorServiceAssignmentJpaEntity assignment, UUID serviceId,
            UUID assignmentId) {
        try {
            long bookingCount = appointmentImpact.countBookingsForAssignment(assignment.doctorProfileId(), serviceId);
            boolean activeCare = visitImpact.hasAnyActiveCare(assignment.doctorProfileId());
            return new AdminMutationImpact("DOCTOR_ASSIGNMENT", assignmentId,
                    bookingCount > 0 || activeCare, bookingCount, activeCare);
        } catch (RuntimeException failure) {
            throw new ClinicServiceException("DEPENDENCY_UNAVAILABLE", failure);
        }
    }
    private void validate(UUID serviceId, AuthenticatedAccount actor, String correlationId) {
        if (serviceId == null || actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.ADMIN)
            throw new ClinicServiceException("ACCESS_DENIED");
        if (correlationId == null || correlationId.isBlank() || correlationId.length()>128) throw invalid();
    }
    private ClinicServiceException invalid() { return new ClinicServiceException("SERVICE_REQUEST_INVALID"); }
}
