package fu.fa26.sep490.g58.otcms_be.servicecatalog;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.DoctorServiceAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.AuditClinicServiceMutation;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ClinicServiceBookingQueryService.BookingAudience;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceCatalogAuditRecorder;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl.ServiceCatalogQueryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.ClinicServiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl.ActivateClinicServiceServiceImpl;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl.CreateClinicServiceServiceImpl;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl.DeactivateClinicServiceServiceImpl;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl.UpdateClinicServiceServiceImpl;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClinicServiceHandlerTest {
    private final ClinicServiceRepository repository = mock(ClinicServiceRepository.class);
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final ServiceCatalogAuditRecorder audits = mock(ServiceCatalogAuditRecorder.class);

    private static ClinicServiceJpaEntity entity(ClinicService service) {
        return ClinicServiceMapper.toEntity(service);
    }

    @Test
    void createNormalizesNameAndPreservesSchedulingValues() {
        when(repository.save(any(ClinicServiceJpaEntity.class))).thenAnswer(call -> call.getArgument(0));
        ClinicService service = new CreateClinicServiceServiceImpl(repository, guard).create("  MRI   Scan ", " description ", 45, 10, true);
        assertEquals("MRI Scan", service.name());
        assertEquals("description", service.description());
        assertEquals(Duration.ofMinutes(45), service.consultationDuration());
        assertEquals(Duration.ofMinutes(10), service.bufferDuration());
        assertTrue(service.active());
        var order = inOrder(guard, repository);
        order.verify(guard).acquire();
        order.verify(repository).save(any(ClinicServiceJpaEntity.class));
    }

    @Test
    void rejectsInvalidDurationOrBuffer() {
        var handler = new CreateClinicServiceServiceImpl(repository, guard);
        assertThrows(ClinicServiceException.class, () -> handler.create("Valid name", null, 0, 0, false));
        assertThrows(ClinicServiceException.class, () -> handler.create("Valid name", null, 10, -1, false));
        verifyNoInteractions(repository);
    }

    @Test
    void deactivationPreservesExistingServiceIdentityAndAuditsTheChange() {
        UUID id = UUID.randomUUID();
        ClinicService existing = new ClinicService(id, "MRI", null, Duration.ofMinutes(45), Duration.ofMinutes(10), true, true);
        when(repository.findById(id)).thenReturn(Optional.of(entity(existing)));
        when(repository.save(any(ClinicServiceJpaEntity.class))).thenAnswer(call -> call.getArgument(0));
        UUID actor = UUID.randomUUID();
        var authenticatedActor = new AuthenticatedAccount(actor, AccountRole.ADMIN, UUID.randomUUID());
        new DeactivateClinicServiceServiceImpl(repository, guard, audits)
                .deactivate(id, authenticatedActor, UUID.randomUUID().toString());
        var order = inOrder(guard, repository, audits);
        order.verify(guard).acquire();
        order.verify(repository).findById(id);
        order.verify(repository).save(any(ClinicServiceJpaEntity.class));
        order.verify(audits).record(eq(id), eq("MRI"), eq(actor), eq("CLINIC_SERVICE"), eq(id), eq("DEACTIVATED"),
                eq(java.util.Map.of("active", true)), eq(java.util.Map.of("active", false)));
    }

    @Test
    void updateAndActivationAcquireGuardBeforeReadingServiceState() {
        UUID id = UUID.randomUUID();
        ClinicService existing = new ClinicService(id, "MRI", null, Duration.ofMinutes(45), Duration.ofMinutes(10), true, true);
        when(repository.findById(id)).thenReturn(Optional.of(entity(existing)));
        when(repository.save(any(ClinicServiceJpaEntity.class))).thenAnswer(call -> call.getArgument(0));

        new UpdateClinicServiceServiceImpl(repository, guard).update(id, "MRI Plus", null, 50, 5, true);
        var updateOrder = inOrder(guard, repository);
        updateOrder.verify(guard).acquire();
        updateOrder.verify(repository).findById(id);
        updateOrder.verify(repository).save(any());

        clearInvocations(guard, repository);
        var actor = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.ADMIN, UUID.randomUUID());
        new ActivateClinicServiceServiceImpl(repository, guard, audits)
                .activate(id, actor, UUID.randomUUID().toString());
        var activationOrder = inOrder(guard, repository);
        activationOrder.verify(guard).acquire();
        activationOrder.verify(repository).findById(id);
    }
    @Test void labCreationAndUpdateKeepThreeNestedRangesAndRejectEqualBounds() {
        var row = new fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator("Hb", "g/dL",
                new fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator.ReferenceRange(13.5, 17),
                new fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator.ReferenceRange(12, 15));
        when(repository.save(any(ClinicServiceJpaEntity.class))).thenAnswer(call -> call.getArgument(0));
        var handler = new CreateClinicServiceServiceImpl(repository, guard);
        var created = handler.create("Blood panel", null,
                fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType.LABORATORY,
                java.math.BigDecimal.TEN, 20, 0, false, null, "Fast beforehand", java.util.List.of(row, row, row));
        assertEquals(3, created.indicators().size());
        assertEquals(13.5, created.indicators().getFirst().refMale().min());
        when(repository.findById(created.id())).thenReturn(Optional.of(entity(created)));
        var updated = new UpdateClinicServiceServiceImpl(repository, guard).update(created.id(), "Blood panel", null,
                java.math.BigDecimal.TEN, 20, 0, true, null, "Updated instructions", created.indicators());
        assertTrue(updated.onlineBookable());
        assertEquals("Updated instructions", updated.patientNote());
        var invalid = new fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator("Hb", "g/dL",
                new fu.fa26.sep490.g58.otcms_be.servicecatalog.model.LabIndicator.ReferenceRange(17, 17), row.refFemale());
        assertThrows(ClinicServiceException.class, () -> handler.create("Invalid panel", null,
                fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType.LABORATORY,
                java.math.BigDecimal.TEN, 20, 0, false, null, null, java.util.List.of(invalid)));
    }

    @Test void createRejectsDuplicateActiveNameAndDefaultsTypeToExaminationAndSupportsImagingModality() {
        var handler = new CreateClinicServiceServiceImpl(repository, guard);
        when(repository.existsByNameIgnoreCaseAndActiveTrue("Existing")).thenReturn(true);
        assertEquals("DUPLICATE_SERVICE_NAME", assertThrows(ClinicServiceException.class,
                () -> handler.create("Existing", null, null, java.math.BigDecimal.TEN, 20, 0, true)).getMessage());
        verify(repository, never()).save(any());

        when(repository.save(any(ClinicServiceJpaEntity.class))).thenAnswer(call -> call.getArgument(0));
        var defaulted = handler.create("Checkup", null, null, java.math.BigDecimal.TEN, 20, 0, true);
        assertEquals(fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType.EXAMINATION, defaulted.type());

        var imaging = handler.create("Knee MRI", "3T",
                fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType.IMAGING,
                java.math.BigDecimal.valueOf(500000), 30, 5, true, "MRI", "No metal", java.util.List.of());
        assertEquals(fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType.IMAGING, imaging.type());
        assertEquals("MRI", imaging.modality());
    }

    @Test void activateActivatesInactiveServiceWithAuditAndValidatesGuards() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var admin = new AuthenticatedAccount(actorId, AccountRole.ADMIN, UUID.randomUUID());
        var doctor = new AuthenticatedAccount(actorId, AccountRole.DOCTOR, UUID.randomUUID());
        var handler = new ActivateClinicServiceServiceImpl(repository, guard, audits);

        assertEquals("ACCESS_DENIED", assertThrows(ClinicServiceException.class,
                () -> handler.activate(id, doctor, "corr-1")).getMessage());
        assertEquals("SERVICE_REQUEST_INVALID", assertThrows(ClinicServiceException.class,
                () -> handler.activate(id, admin, "   ")).getMessage());
        assertEquals("SERVICE_REQUEST_INVALID", assertThrows(ClinicServiceException.class,
                () -> handler.activate(id, admin, "c".repeat(129))).getMessage());
        verifyNoInteractions(guard, repository);

        when(repository.findById(id)).thenReturn(Optional.empty());
        assertEquals("SERVICE_NOT_FOUND", assertThrows(ClinicServiceException.class,
                () -> handler.activate(id, admin, "corr-1")).getMessage());

        ClinicService inactive = new ClinicService(id, "MRI", null, Duration.ofMinutes(45), Duration.ofMinutes(10), true, false);
        when(repository.findById(id)).thenReturn(Optional.of(entity(inactive)));
        when(repository.save(any(ClinicServiceJpaEntity.class))).thenAnswer(call -> call.getArgument(0));
        handler.activate(id, admin, "corr-1");

        verify(repository).save(argThat(ClinicServiceJpaEntity::active));
        verify(audits).record(eq(id), eq("MRI"), eq(actorId), eq("CLINIC_SERVICE"), eq(id), eq("UPDATED"),
                eq(java.util.Map.of("active", false)), eq(java.util.Map.of("active", true)));
    }

    @Test void deactivateValidatesGuardsAndNoOpsWhenAlreadyInactiveOrMissing() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var admin = new AuthenticatedAccount(actorId, AccountRole.ADMIN, UUID.randomUUID());
        var doctor = new AuthenticatedAccount(actorId, AccountRole.DOCTOR, UUID.randomUUID());
        var handler = new DeactivateClinicServiceServiceImpl(repository, guard, audits);

        assertEquals("ACCESS_DENIED", assertThrows(ClinicServiceException.class,
                () -> handler.deactivate(id, doctor, "corr-1")).getMessage());
        assertEquals("SERVICE_REQUEST_INVALID", assertThrows(ClinicServiceException.class,
                () -> handler.deactivate(id, admin, "   ")).getMessage());
        assertEquals("SERVICE_REQUEST_INVALID", assertThrows(ClinicServiceException.class,
                () -> handler.deactivate(id, admin, "c".repeat(129))).getMessage());
        verifyNoInteractions(guard, repository);

        when(repository.findById(id)).thenReturn(Optional.empty());
        assertEquals("SERVICE_NOT_FOUND", assertThrows(ClinicServiceException.class,
                () -> handler.deactivate(id, admin, "corr-1")).getMessage());

        ClinicService alreadyInactive = new ClinicService(id, "MRI", null, Duration.ofMinutes(45), Duration.ofMinutes(10), true, false);
        when(repository.findById(id)).thenReturn(Optional.of(entity(alreadyInactive)));
        handler.deactivate(id, admin, "corr-1");

        verify(repository, never()).save(any());
        verifyNoInteractions(audits);
    }

    @Test void auditMutationValidatesInputsAndRecordsAuditOnSuccess() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var admin = new AuthenticatedAccount(actorId, AccountRole.ADMIN, UUID.randomUUID());
        var doctor = new AuthenticatedAccount(actorId, AccountRole.DOCTOR, UUID.randomUUID());
        var auditMutation = new AuditClinicServiceMutation(audits, repository);

        assertEquals("ACCESS_DENIED", assertThrows(ClinicServiceException.class,
                () -> auditMutation.execute("CREATED", doctor, "corr-1", () -> null)).getMessage());
        assertEquals("SERVICE_REQUEST_INVALID", assertThrows(ClinicServiceException.class,
                () -> auditMutation.execute("CREATED", admin, "c".repeat(129), () -> null)).getMessage());
        assertEquals("SERVICE_REQUEST_INVALID", assertThrows(ClinicServiceException.class,
                () -> auditMutation.execute("DELETED", admin, "corr-1", () -> null)).getMessage());

        ClinicService created = new ClinicService(id, "MRI", null, Duration.ofMinutes(45), Duration.ofMinutes(10), true, true);
        var result = auditMutation.execute("CREATED", admin, "corr-1", () -> created);
        assertEquals(created, result);
        verify(repository).flush();
        verify(audits).record(eq(id), eq("MRI"), eq(actorId), eq("CLINIC_SERVICE"), eq(id), eq("CREATED"),
                isNull(), eq(java.util.Map.of()));
    }

    @Test void serviceCatalogQueryGuardsNullServiceIdsWithoutQueryingRepository() {
        var assignmentsRepo = mock(DoctorServiceAssignmentRepository.class);
        var queryService = new ServiceCatalogQueryServiceImpl(repository, assignmentsRepo);

        assertEquals("SERVICE_NOT_BOOKABLE", assertThrows(ClinicServiceException.class,
                () -> queryService.findActiveService(null, BookingAudience.PUBLIC_PATIENT)).getMessage());
        assertTrue(queryService.findServiceLabel(null).isEmpty());
        verifyNoInteractions(repository, assignmentsRepo);
    }
}
