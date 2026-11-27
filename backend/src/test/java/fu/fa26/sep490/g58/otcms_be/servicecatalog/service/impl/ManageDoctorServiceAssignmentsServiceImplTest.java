package fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response.DoctorAssignmentResponse;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.ClinicServiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.DoctorServiceAssignmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.DoctorServiceAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceCatalogAuditRecorder;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitMutationImpactService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManageDoctorServiceAssignmentsServiceImplTest {
    private final UUID serviceId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final AuthenticatedAccount admin = new AuthenticatedAccount(actorId, AccountRole.ADMIN, UUID.randomUUID());
    private final ClinicServiceRepository services = mock(ClinicServiceRepository.class);
    private final DoctorServiceAssignmentRepository assignments = mock(DoctorServiceAssignmentRepository.class);
    private final ServiceCatalogAuditRecorder audits = mock(ServiceCatalogAuditRecorder.class);
    private final DoctorProfileQueryService doctors = mock(DoctorProfileQueryService.class);
    private final AppointmentMutationImpactService appointments = mock(AppointmentMutationImpactService.class);
    private final VisitMutationImpactService visits = mock(VisitMutationImpactService.class);
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final ManageDoctorServiceAssignmentsServiceImpl service = new ManageDoctorServiceAssignmentsServiceImpl(
            services, assignments, audits, doctors, appointments, visits, guard);

    private ClinicServiceJpaEntity activeService() {
        return new ClinicServiceJpaEntity(serviceId, "Service", "description", 30, 0, true, true);
    }

    private ClinicServiceJpaEntity inactiveService() {
        return new ClinicServiceJpaEntity(serviceId, "Service", "description", 30, 0, true, false);
    }

    @Test
    void listReturnsActiveAssignmentsOfAKnownService() {
        when(services.existsById(serviceId)).thenReturn(true);
        when(assignments.findByServiceIdAndActiveTrueOrderByDoctorProfileIdAsc(serviceId)).thenReturn(List.of(
                new DoctorServiceAssignmentJpaEntity(assignmentId, doctorId, serviceId, true)));

        List<DoctorAssignmentResponse> result = service.list(serviceId);

        assertEquals(1, result.size());
        assertEquals(assignmentId, result.get(0).getId());
        assertEquals(serviceId, result.get(0).getServiceId());
        assertEquals(doctorId, result.get(0).getDoctorProfileId());
    }

    @Test
    void listRejectsNullServiceIdAsNotFound() {
        assertEquals("SERVICE_NOT_FOUND",
                assertThrows(ClinicServiceException.class, () -> service.list(null)).getMessage());
        verifyNoInteractions(services, assignments);
    }

    @Test
    void listRejectsUnknownServiceWithoutReadingAssignments() {
        when(services.existsById(serviceId)).thenReturn(false);

        assertEquals("SERVICE_NOT_FOUND",
                assertThrows(ClinicServiceException.class, () -> service.list(serviceId)).getMessage());
        verifyNoInteractions(assignments);
    }

    @Test
    void listWithActorAllowsOnlyAdminOnAKnownService() {
        var doctor = new AuthenticatedAccount(actorId, AccountRole.DOCTOR, UUID.randomUUID());
        assertEquals("ACCESS_DENIED",
                assertThrows(ClinicServiceException.class, () -> service.list(null, admin)).getMessage());
        assertEquals("ACCESS_DENIED",
                assertThrows(ClinicServiceException.class, () -> service.list(serviceId, null)).getMessage());
        assertEquals("ACCESS_DENIED",
                assertThrows(ClinicServiceException.class, () -> service.list(serviceId, doctor)).getMessage());
        verifyNoInteractions(services, assignments);

        when(services.existsById(serviceId)).thenReturn(true);
        when(assignments.findByServiceIdAndActiveTrueOrderByDoctorProfileIdAsc(serviceId)).thenReturn(List.of());
        assertEquals(List.of(), service.list(serviceId, admin));
    }

    @Test
    void assignCreatesActiveAssignmentAndRecordsCreatedAudit() {
        when(services.findById(serviceId)).thenReturn(Optional.of(activeService()));
        when(doctors.existsDoctorProfile(doctorId)).thenReturn(true);
        when(assignments.existsByDoctorProfileIdAndServiceIdAndActiveTrue(doctorId, serviceId)).thenReturn(false);

        DoctorAssignmentResponse response = service.assign(serviceId, doctorId, admin, "corr-1");

        assertEquals(serviceId, response.getServiceId());
        assertEquals(doctorId, response.getDoctorProfileId());
        ArgumentCaptor<DoctorServiceAssignmentJpaEntity> saved =
                ArgumentCaptor.forClass(DoctorServiceAssignmentJpaEntity.class);
        verify(assignments).save(saved.capture());
        assertEquals(response.getId(), saved.getValue().id());
        assertTrue(saved.getValue().active());
        verify(audits).record(eq(serviceId), eq("Service"), eq(actorId), eq("DOCTOR_ASSIGNMENT"),
                eq(response.getId()), eq("CREATED"), isNull(),
                eq(Map.of("doctorProfileId", doctorId, "active", true)));
    }

    @Test
    void assignRejectsMissingDoctorIdBeforeLocking() {
        assertEquals("SERVICE_REQUEST_INVALID", assertThrows(ClinicServiceException.class,
                () -> service.assign(serviceId, null, admin, "corr-1")).getMessage());
        verifyNoInteractions(guard, services, doctors, assignments);
    }

    @Test
    void assignRejectsInactiveOrMissingService() {
        when(services.findById(serviceId)).thenReturn(Optional.of(inactiveService()));
        assertEquals("SERVICE_NOT_FOUND", assertThrows(ClinicServiceException.class,
                () -> service.assign(serviceId, doctorId, admin, "corr-1")).getMessage());

        when(services.findById(serviceId)).thenReturn(Optional.empty());
        assertEquals("SERVICE_NOT_FOUND", assertThrows(ClinicServiceException.class,
                () -> service.assign(serviceId, doctorId, admin, "corr-1")).getMessage());
        verifyNoInteractions(doctors, assignments, audits);
    }

    @Test
    void assignRejectsUnknownDoctorProfileAndDuplicateActiveAssignment() {
        when(services.findById(serviceId)).thenReturn(Optional.of(activeService()));
        when(doctors.existsDoctorProfile(doctorId)).thenReturn(false);
        assertEquals("DOCTOR_PROFILE_NOT_FOUND", assertThrows(ClinicServiceException.class,
                () -> service.assign(serviceId, doctorId, admin, "corr-1")).getMessage());

        when(doctors.existsDoctorProfile(doctorId)).thenReturn(true);
        when(assignments.existsByDoctorProfileIdAndServiceIdAndActiveTrue(doctorId, serviceId)).thenReturn(true);
        assertEquals("DOCTOR_ASSIGNMENT_ALREADY_EXISTS", assertThrows(ClinicServiceException.class,
                () -> service.assign(serviceId, doctorId, admin, "corr-1")).getMessage());
        verify(assignments, never()).save(any());
        verifyNoInteractions(audits);
    }

    @Test
    void assignAndRemoveRejectNonAdminActorAndUnusableCorrelationIdBeforeLocking() {
        var doctor = new AuthenticatedAccount(actorId, AccountRole.DOCTOR, UUID.randomUUID());
        assertEquals("ACCESS_DENIED", assertThrows(ClinicServiceException.class,
                () -> service.assign(serviceId, doctorId, doctor, "corr-1")).getMessage());
        assertEquals("ACCESS_DENIED", assertThrows(ClinicServiceException.class,
                () -> service.remove(serviceId, assignmentId, null, "corr-1")).getMessage());
        assertEquals("SERVICE_REQUEST_INVALID", assertThrows(ClinicServiceException.class,
                () -> service.assign(serviceId, doctorId, admin, "   ")).getMessage());
        assertEquals("SERVICE_REQUEST_INVALID", assertThrows(ClinicServiceException.class,
                () -> service.assign(serviceId, doctorId, admin, null)).getMessage());
        assertEquals("SERVICE_REQUEST_INVALID", assertThrows(ClinicServiceException.class,
                () -> service.remove(serviceId, assignmentId, admin, "c".repeat(129))).getMessage());
        verifyNoInteractions(guard);
    }

    @Test
    void removeDeactivatesAssignmentWhenNoBookingOrActiveCareBlocksIt() {
        var assignment = new DoctorServiceAssignmentJpaEntity(assignmentId, doctorId, serviceId, true);
        when(services.findById(serviceId)).thenReturn(Optional.of(activeService()));
        when(assignments.findByIdAndServiceIdAndActiveTrue(assignmentId, serviceId)).thenReturn(Optional.of(assignment));
        when(appointments.countBookingsForAssignment(doctorId, serviceId)).thenReturn(0L);
        when(visits.hasAnyActiveCare(doctorId)).thenReturn(false);

        service.remove(serviceId, assignmentId, admin, "corr-1");

        assertFalse(assignment.active());
        verify(assignments).save(assignment);
        verify(audits).record(eq(serviceId), eq("Service"), eq(actorId), eq("DOCTOR_ASSIGNMENT"),
                eq(assignmentId), eq("DELETED"), eq(Map.of("doctorProfileId", doctorId, "active", true)),
                eq(Map.of("active", false)));
    }

    @Test
    void removeRejectsMissingAssignmentIdBeforeLocking() {
        assertEquals("SERVICE_REQUEST_INVALID", assertThrows(ClinicServiceException.class,
                () -> service.remove(serviceId, null, admin, "corr-1")).getMessage());
        verifyNoInteractions(guard, assignments);
    }

    @Test
    void assessRemovalImpactRequiresAdminAndCompleteIds() {
        var doctor = new AuthenticatedAccount(actorId, AccountRole.DOCTOR, UUID.randomUUID());
        assertEquals("ACCESS_DENIED", assertThrows(ClinicServiceException.class,
                () -> service.assessRemovalImpact(null, assignmentId, admin)).getMessage());
        assertEquals("ACCESS_DENIED", assertThrows(ClinicServiceException.class,
                () -> service.assessRemovalImpact(serviceId, null, admin)).getMessage());
        assertEquals("ACCESS_DENIED", assertThrows(ClinicServiceException.class,
                () -> service.assessRemovalImpact(serviceId, assignmentId, doctor)).getMessage());
        verifyNoInteractions(guard, services, assignments);
    }

    @Test
    void assessRemovalImpactReportsBookingsWithoutWritingAssignment() {
        var assignment = new DoctorServiceAssignmentJpaEntity(assignmentId, doctorId, serviceId, true);
        when(services.findById(serviceId)).thenReturn(Optional.of(activeService()));
        when(assignments.findByIdAndServiceIdAndActiveTrue(assignmentId, serviceId)).thenReturn(Optional.of(assignment));
        when(appointments.countBookingsForAssignment(doctorId, serviceId)).thenReturn(2L);
        when(visits.hasAnyActiveCare(doctorId)).thenReturn(false);

        AdminMutationImpact impact = service.assessRemovalImpact(serviceId, assignmentId, admin);

        assertTrue(impact.isBlocked());
        assertEquals("DOCTOR_ASSIGNMENT", impact.getResourceType());
        assertEquals(assignmentId, impact.getResourceId());
        assertEquals(2, impact.getAffectedBookingCount());
        assertFalse(impact.isActiveCareBlocks());
        verify(guard).acquire();
        verify(assignments, never()).save(any());
        verifyNoInteractions(audits);
    }

    @Test
    void assessRemovalImpactRejectsUnknownServiceOrAssignment() {
        when(services.findById(serviceId)).thenReturn(Optional.empty());
        assertEquals("SERVICE_NOT_FOUND", assertThrows(ClinicServiceException.class,
                () -> service.assessRemovalImpact(serviceId, assignmentId, admin)).getMessage());

        when(services.findById(serviceId)).thenReturn(Optional.of(activeService()));
        when(assignments.findByIdAndServiceIdAndActiveTrue(assignmentId, serviceId)).thenReturn(Optional.empty());
        assertEquals("DOCTOR_ASSIGNMENT_NOT_FOUND", assertThrows(ClinicServiceException.class,
                () -> service.assessRemovalImpact(serviceId, assignmentId, admin)).getMessage());
    }
}
