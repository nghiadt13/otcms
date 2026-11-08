package fu.fa26.sep490.g58.otcms_be.staff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffRoomAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.impl.StaffRoomAssignmentQueryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.staff.integration.diagnostic.StaffDiagnosticRoomStatusAdapter;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StaffRoomAssignmentQueryServiceTest {
    final UUID actor = UUID.randomUUID(), room = UUID.randomUUID();
    final Instant at = Instant.parse("2026-11-05T05:00:00Z");
    final StaffAccountDirectory accounts = mock(StaffAccountDirectory.class);
    final StaffRoomAssignmentRepository assignments = mock(StaffRoomAssignmentRepository.class);
    final StaffRoomAssignmentQueryServiceImpl query = new StaffRoomAssignmentQueryServiceImpl(accounts, assignments);
    @Test void activeTechnicianWithCurrentAssignmentIsAuthorizedForExactRoomAndAccount() {
        active(AccountType.IMAGING_TECHNICIAN);
        when(assignments.hasActiveAssignment(actor, room, at)).thenReturn(true);
        var port = new StaffDiagnosticRoomStatusAdapter(query, Clock.fixed(at, ZoneOffset.UTC));
        var result = port.authorizeAssignedTechnician(room, actor);
        assertTrue(result.active()); assertEquals(actor, result.actorId()); assertEquals(room, result.roomId());
        verify(assignments).hasActiveAssignment(actor, room, at);
    }
    @Test void missingCurrentAssignmentIsRejectedByOwnerQuery() {
        active(AccountType.IMAGING_TECHNICIAN);
        assertFalse(query.isImagingTechnicianAssigned(actor, room, at));
        verify(assignments).hasActiveAssignment(actor, room, at);
    }
    @Test void inactiveOrMissingAccountFailsBeforeAssignmentRead() {
        when(accounts.activeStaff(actor)).thenReturn(Optional.empty());
        assertFalse(query.isImagingTechnicianAssigned(actor, room, at)); verifyNoInteractions(assignments);
    }
    @ParameterizedTest @EnumSource(value = AccountType.class, names = "IMAGING_TECHNICIAN", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesCannotUseTechnicianScope(AccountType role) {
        active(role); assertFalse(query.isImagingTechnicianAssigned(actor, room, at)); verifyNoInteractions(assignments);
    }
    @Test void mismatchedAccountEvidenceFailsClosed() {
        when(accounts.activeStaff(actor)).thenReturn(Optional.of(new StaffAccountDirectory.Entry(UUID.randomUUID(), "", "", AccountType.IMAGING_TECHNICIAN)));
        assertFalse(query.isImagingTechnicianAssigned(actor, room, at)); verifyNoInteractions(assignments);
    }
    @Test void nullIdentifiersAndInstantFailBeforeReads() {
        assertFalse(query.isImagingTechnicianAssigned(null, room, at));
        assertFalse(query.isImagingTechnicianAssigned(actor, null, at));
        assertFalse(query.isImagingTechnicianAssigned(actor, room, null)); verifyNoInteractions(accounts, assignments);
    }
    @Test void persistenceFailureDoesNotBecomeAuthorization() {
        active(AccountType.IMAGING_TECHNICIAN);
        when(assignments.hasActiveAssignment(actor, room, at)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> query.isImagingTechnicianAssigned(actor, room, at));
    }
    @Test void diagnosticStatusUsesRealAssignmentCompositionWithoutImagingRuntime() {
        active(AccountType.IMAGING_TECHNICIAN);
        when(assignments.hasActiveAssignment(actor, room, at)).thenReturn(true);
        var requests = mock(fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository.class);
        org.springframework.beans.factory.ObjectProvider<fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticReadPort> visitPorts = mock(org.springframework.beans.factory.ObjectProvider.class);
        org.springframework.beans.factory.ObjectProvider<fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomStatusAccessPort> roomPorts = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(roomPorts.getIfAvailable()).thenReturn(new StaffDiagnosticRoomStatusAdapter(query, Clock.fixed(at, ZoneOffset.UTC)));
        UUID requestId = UUID.randomUUID();
        var request = new fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticRequestJpaEntity(requestId,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), room, "MRI",
                fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus.PENDING, null, null, null);
        when(requests.findById(requestId)).thenReturn(Optional.of(request));
        var service = new fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticStatusServiceImpl(requests, visitPorts, roomPorts);
        var technician = fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor.user(actor,
                fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.IMAGING_TECHNICIAN);
        assertEquals(requestId, service.getStatus(technician, requestId).requestId());
        when(assignments.hasActiveAssignment(actor, room, at)).thenReturn(false);
        assertThrows(fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException.class,
                () -> service.getStatus(technician, requestId));
        verifyNoInteractions(visitPorts);
    }

    void active(AccountType role) {
        when(accounts.activeStaff(actor)).thenReturn(Optional.of(new StaffAccountDirectory.Entry(actor, "", "", role)));
    }
}
