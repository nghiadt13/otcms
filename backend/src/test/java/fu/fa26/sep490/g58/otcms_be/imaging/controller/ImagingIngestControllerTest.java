package fu.fa26.sep490.g58.otcms_be.imaging.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingIngestService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingUploadCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;

class ImagingIngestControllerTest {
    private final ImagingIngestService ingest = mock(ImagingIngestService.class);
    private final ImagingIngestController controller = new ImagingIngestController(ingest);

    @Test
    void uploadStreamsRequestAndUsesAuthenticatedTechnicianIdentity() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        var now = Instant.parse("2026-11-05T00:00:00Z");
        when(ingest.upload(any(ImagingUploadCommand.class))).thenReturn(
                new ImagingOperationStatus(operationId, ImagingOperationState.PROCESSING, now, now, null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/zip");
        request.setContent(new byte[] {1, 2, 3});

        var response = controller.upload(requestId, attemptId, "upload-key-1",
                new AuthenticatedAccount(actorId, AccountRole.IMAGING_TECHNICIAN, UUID.randomUUID()), request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("/api/v1/imaging/ingest-operations/" + operationId,
                response.getHeaders().getLocation().toString());
        ArgumentCaptor<ImagingUploadCommand> command = ArgumentCaptor.forClass(ImagingUploadCommand.class);
        verify(ingest).upload(command.capture());
        assertEquals(actorId, command.getValue().actorAccountId());
        assertEquals(requestId, command.getValue().requestId());
        assertEquals(attemptId, command.getValue().attemptId());
        assertEquals("upload-key-1", command.getValue().idempotencyKey());
        assertEquals("application/zip", command.getValue().contentType());
    }

    @Test
    void operationReadsAndSendUseAuthenticatedActorAndStableOperationReference() {
        UUID actorId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        var now = Instant.parse("2026-11-05T00:00:00Z");
        var actor = new AuthenticatedAccount(actorId, AccountRole.IMAGING_TECHNICIAN, UUID.randomUUID());
        when(ingest.getOperationStatus(actorId, operationId)).thenReturn(
                new ImagingOperationStatus(operationId, ImagingOperationState.ARCHIVED_NOT_SENT, now, now, null));
        when(ingest.send(actorId, operationId, "send-key-1")).thenReturn(
                new ImagingOperationStatus(operationId, ImagingOperationState.CONFIRMATION_PENDING, now, now, null));

        var read = controller.getOperation(operationId, actor);
        var sent = controller.send(operationId, "send-key-1", actor);

        assertEquals(HttpStatus.OK, read.getStatusCode());
        assertEquals("ARCHIVED_NOT_SENT", read.getBody().state());
        assertEquals(HttpStatus.ACCEPTED, sent.getStatusCode());
        assertEquals("CONFIRMATION_PENDING", sent.getBody().state());
        verify(ingest).getOperationStatus(actorId, operationId);
        verify(ingest).send(actorId, operationId, "send-key-1");
    }
}
