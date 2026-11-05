package fu.fa26.sep490.g58.otcms_be.imaging.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecision;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecisionCommand;
import fu.fa26.sep490.g58.otcms_be.imaging.model.dto.request.ImagingReconciliationDecisionRequest;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingAuditPersistenceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

@ExtendWith(MockitoExtension.class)
class ImagingReconciliationControllerTest {
    @Mock ImagingReconciliationService reconciliation;
    @Mock ImagingAuditPersistenceService audit;

    @Test
    void technicianCannotReadTheReconciliationQueue() {
        var controller = new ImagingReconciliationController(reconciliation, audit);
        var technician = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.IMAGING_TECHNICIAN, UUID.randomUUID());
        HttpServletRequest request = new MockHttpServletRequest();

        ImagingOperationException denied = assertThrows(ImagingOperationException.class,
                () -> controller.list(null, null, null, 0, 25, technician, request));

        assertEquals(ImagingOperationOutcome.UNAUTHORIZED_RECORD, denied.outcome());
        verifyNoInteractions(reconciliation);
        verify(audit).recordActor(eq(technician.accountId()), eq("IMAGING_RECONCILIATION_ACCESS"), eq("DENIED"),
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq("unknown"), any());
    }

    @Test
    void technicianCannotRecordAResolutionDecision() {
        var controller = new ImagingReconciliationController(reconciliation, audit);
        var technician = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.IMAGING_TECHNICIAN, UUID.randomUUID());
        HttpServletRequest request = new MockHttpServletRequest();

        ImagingOperationException denied = assertThrows(ImagingOperationException.class,
                () -> controller.decide(UUID.randomUUID(), new ImagingReconciliationDecisionRequest(
                        ImagingReconciliationDecision.KEEP_QUARANTINED, "INC-42"), technician, request));

        assertEquals(ImagingOperationOutcome.UNAUTHORIZED_RECORD, denied.outcome());
        verifyNoInteractions(reconciliation);
        verify(audit).recordActor(eq(technician.accountId()), eq("IMAGING_RECONCILIATION_ACCESS"), eq("DENIED"),
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq("unknown"), any());
    }

    @Test
    void adminCanReadTheQueue() {
        var controller = new ImagingReconciliationController(reconciliation, audit);
        var admin = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.ADMIN, UUID.randomUUID());
        HttpServletRequest request = new MockHttpServletRequest();
        when(reconciliation.listQueue(eq(admin.accountId()), any(), eq("unknown")))
                .thenReturn(Page.empty());

        var response = controller.list(null, null, null, 0, 25, admin, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, response.getBody().totalElements());
        verify(reconciliation).listQueue(eq(admin.accountId()), any(), eq("unknown"));
    }

    @Test
    void adminCanRecordAResolutionDecision() {
        var controller = new ImagingReconciliationController(reconciliation, audit);
        var admin = new AuthenticatedAccount(UUID.randomUUID(), AccountRole.ADMIN, UUID.randomUUID());
        UUID caseId = UUID.randomUUID();
        HttpServletRequest request = new MockHttpServletRequest();

        var response = controller.decide(caseId, new ImagingReconciliationDecisionRequest(
                ImagingReconciliationDecision.KEEP_QUARANTINED, "INC-42"), admin, request);

        assertEquals(204, response.getStatusCode().value());
        verify(reconciliation).recordDecision(admin.accountId(), caseId,
                new ImagingReconciliationDecisionCommand(ImagingReconciliationDecision.KEEP_QUARANTINED,
                        "INC-42"), "unknown");
    }
}
