package fu.fa26.sep490.g58.otcms_be.servicecatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.common.exception.ResourceMutationBlockedException;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.ClinicServiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.DoctorServiceAssignmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.DoctorServiceAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ServiceCatalogManagementAuditRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl.ManageDoctorServiceAssignmentsServiceImpl;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitMutationImpactService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManageDoctorServiceAssignmentsT032Test {
    private final UUID serviceId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final AuthenticatedAccount admin = new AuthenticatedAccount(actorId, AccountRole.ADMIN, UUID.randomUUID());

    @Mock private ClinicServiceRepository services;
    @Mock private DoctorServiceAssignmentRepository assignments;
    @Mock private ServiceCatalogManagementAuditRepository audits;
    @Mock private DoctorProfileQueryService doctors;
    @Mock private AppointmentMutationImpactService appointments;
    @Mock private VisitMutationImpactService visits;
    @Mock private CoordinationGuardService guard;
    private ManageDoctorServiceAssignmentsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ManageDoctorServiceAssignmentsServiceImpl(services, assignments, audits, doctors,
                appointments, visits, guard, Clock.fixed(Instant.parse("2026-10-20T09:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void removalIsBlockedByAnyActiveCareAndDoesNotChangeAssignmentOrAudit() {
        var clinicService = new ClinicServiceJpaEntity(serviceId, "Service", "description", 30, 0, true, true);
        var assignment = new DoctorServiceAssignmentJpaEntity(assignmentId, doctorId, serviceId, true);
        when(services.findById(serviceId)).thenReturn(Optional.of(clinicService));
        when(assignments.findByIdAndServiceIdAndActiveTrue(assignmentId, serviceId)).thenReturn(Optional.of(assignment));
        when(appointments.countBookingsForAssignment(doctorId, serviceId)).thenReturn(0L);
        when(visits.hasAnyActiveCare(doctorId)).thenReturn(true);

        ResourceMutationBlockedException failure = assertThrows(ResourceMutationBlockedException.class,
                () -> service.remove(serviceId, assignmentId, admin, "t032-correlation"));

        assertTrue(failure.impact().isBlocked());
        assertEquals("DOCTOR_ASSIGNMENT", failure.impact().getResourceType());
        assertEquals(assignmentId, failure.impact().getResourceId());
        assertEquals(0, failure.impact().getAffectedBookingCount());
        assertTrue(failure.impact().isActiveCareBlocks());
        verify(guard).acquire();
        verify(assignments, never()).save(any());
        verify(audits, never()).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void removalFailsClosedWhenVisitImpactCannotBeRead() {
        var clinicService = new ClinicServiceJpaEntity(serviceId, "Service", "description", 30, 0, true, true);
        var assignment = new DoctorServiceAssignmentJpaEntity(assignmentId, doctorId, serviceId, true);
        when(services.findById(serviceId)).thenReturn(Optional.of(clinicService));
        when(assignments.findByIdAndServiceIdAndActiveTrue(assignmentId, serviceId)).thenReturn(Optional.of(assignment));
        when(appointments.countBookingsForAssignment(doctorId, serviceId)).thenReturn(0L);
        when(visits.hasAnyActiveCare(doctorId)).thenThrow(new IllegalStateException("Visit query unavailable"));

        ClinicServiceException failure = assertThrows(ClinicServiceException.class,
                () -> service.remove(serviceId, assignmentId, admin, "t032-correlation"));

        assertEquals("DEPENDENCY_UNAVAILABLE", failure.getMessage());
        verify(assignments, never()).save(any());
        verify(audits, never()).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
