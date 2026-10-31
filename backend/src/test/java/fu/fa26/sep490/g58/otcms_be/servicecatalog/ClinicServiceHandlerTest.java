package fu.fa26.sep490.g58.otcms_be.servicecatalog;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ServiceCatalogManagementAuditRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.ClinicServiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.mapper.ClinicServiceMapper;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl.ActivateClinicServiceServiceImpl;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl.CreateClinicServiceServiceImpl;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl.DeactivateClinicServiceServiceImpl;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.impl.UpdateClinicServiceServiceImpl;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClinicServiceHandlerTest {
    private final ClinicServiceRepository repository = mock(ClinicServiceRepository.class);
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final ServiceCatalogManagementAuditRepository audits = mock(ServiceCatalogManagementAuditRepository.class);

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
        new DeactivateClinicServiceServiceImpl(repository, guard, audits, Clock.systemUTC())
                .deactivate(id, authenticatedActor, UUID.randomUUID().toString());
        var order = inOrder(guard, repository, audits);
        order.verify(guard).acquire();
        order.verify(repository).findById(id);
        order.verify(repository).save(any(ClinicServiceJpaEntity.class));
        order.verify(audits).append(any(), eq(id), eq(actor), eq("CLINIC_SERVICE"), eq(id), eq("DEACTIVATED"),
                anyString(), anyString(), any(), anyString());
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
        new ActivateClinicServiceServiceImpl(repository, guard, audits, Clock.systemUTC())
                .activate(id, actor, UUID.randomUUID().toString());
        var activationOrder = inOrder(guard, repository);
        activationOrder.verify(guard).acquire();
        activationOrder.verify(repository).findById(id);
    }
}
