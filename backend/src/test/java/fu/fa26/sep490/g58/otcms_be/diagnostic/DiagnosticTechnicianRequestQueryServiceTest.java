package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.ImagingExecutionTestFixtures.Row;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticTechnicianRequestQueryServiceImpl;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DiagnosticTechnicianRequestQueryServiceTest {
    final UUID id = UUID.randomUUID(), visit = UUID.randomUUID(), patient = UUID.randomUUID(), room = UUID.randomUUID(), orderId = id, serviceId = UUID.randomUUID();
    final DiagnosticActor actor = DiagnosticActor.user(UUID.randomUUID(), AccountRole.IMAGING_TECHNICIAN);
    final DiagnosticStatusService statuses = mock(DiagnosticStatusService.class);
    final ClinicalServiceOrderExecutionPort requests = mock(ClinicalServiceOrderExecutionPort.class);
    final ClinicalOrderingService orders = mock(ClinicalOrderingService.class);
    final DiagnosticTechnicianRequestQueryServiceImpl query = new DiagnosticTechnicianRequestQueryServiceImpl(statuses, requests, orders);
    Row request;
    @BeforeEach void setup() {
        request = new Row(id, visit, patient, UUID.randomUUID(), serviceId, room, "MRI", DiagnosticRequestStatus.PENDING, null, null, null);
        request.setBodyArea("Knee"); request.setClinicalReason("Pain");
        when(statuses.getStatus(actor, id)).thenReturn(new DiagnosticOperationalStatus(id, visit, "MRI", room, DiagnosticRequestStatus.PENDING, 0, null, null));
        when(requests.find(id)).thenAnswer(ignored -> Optional.of(ImagingExecutionTestFixtures.execution(request)));
        when(orders.getImagingOrder(orderId)).thenReturn(order(patient));
    }
    ClinicalOrderingService.OrderView order(UUID patientId) {
        return new ClinicalOrderingService.OrderView(orderId, "SO", visit, patientId, UUID.randomUUID(), serviceId, "MRI", ClinicalServiceType.IMAGING,
                UUID.randomUUID(), "Pain", "Knee", "Weight bearing", null, ServiceOrderStatus.ORDERED, ServiceOrderPaymentStatus.PENDING_PAYMENT);
    }
    @Test void assignedTechnicianGetsPendingClinicalDetailsAndOwnerInstructions() {
        var result = query.getPendingRequest(actor, id);
        assertEquals(patient, result.patientId()); assertEquals(request.getOrderingDoctorId(), result.requestingDoctorAccountId());
        assertEquals("Knee", result.bodyArea()); assertEquals("Pain", result.clinicalReason()); assertEquals("Weight bearing", result.instructions());
        var sequence = inOrder(statuses, requests, orders); sequence.verify(statuses).getStatus(actor, id);
        sequence.verify(requests).find(id); sequence.verify(orders).getImagingOrder(orderId);
        assertFalse(result.toString().contains("Pain"));
    }
    @ParameterizedTest @EnumSource(value = AccountRole.class, names = "IMAGING_TECHNICIAN", mode = EnumSource.Mode.EXCLUDE)
    void otherActorsDeniedBeforeClinicalRead(AccountRole role) {
        assertThrows(DiagnosticOperationException.class, () -> query.getPendingRequest(DiagnosticActor.user(UUID.randomUUID(), role), id));
        verifyNoInteractions(statuses, requests, orders);
    }
    @Test void unassignedOrInactiveTechnicianDenialStopsClinicalRead() {
        when(statuses.getStatus(actor, id)).thenThrow(new DiagnosticOperationException(DiagnosticOperationOutcome.RECORD_NOT_FOUND));
        assertThrows(DiagnosticOperationException.class, () -> query.getPendingRequest(actor, id)); verifyNoInteractions(requests, orders);
    }
    @Test void mismatchedClinicalOrderFailsClosed() {
        when(orders.getImagingOrder(orderId)).thenReturn(order(UUID.randomUUID()));
        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, assertThrows(DiagnosticOperationException.class, () -> query.getPendingRequest(actor, id)).outcome());
    }
    @Test void requestWithoutClinicalOrderIsRejectedBeforeReadingClinicalDetails() {
        when(requests.find(id)).thenReturn(Optional.empty());
        assertThrows(DiagnosticOperationException.class, () -> query.getPendingRequest(actor, id));
        verifyNoInteractions(orders);
    }
    @Test void missingRequestRejected() {
        when(requests.find(id)).thenReturn(Optional.empty());
        assertThrows(DiagnosticOperationException.class, () -> query.getPendingRequest(actor, id)); verifyNoInteractions(orders);
    }
    @Test void mismatchedRoomEvidenceRejectsBeforeOrderRead() {
        request.setRoomId(UUID.randomUUID()); assertThrows(DiagnosticOperationException.class, () -> query.getPendingRequest(actor, id)); verifyNoInteractions(orders);
    }
}
