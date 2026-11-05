package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.integration.orthanc.ArchiveInstanceReceipt;
import fu.fa26.sep490.g58.otcms_be.imaging.integration.orthanc.ImagingArchivePort;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingDicomStudyValidation;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationState;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingOperationType;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingReconciliationService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingUploadCommand;
import fu.fa26.sep490.g58.otcms_be.imaging.validation.ImagingDicomStudyValidator;
import fu.fa26.sep490.g58.otcms_be.imaging.validation.ValidatedDicomStudy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImagingUploadOrchestratorTest {
    private static final ImagingAssignedWork WORK = new ImagingAssignedWork(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "assignment-v3", true);

    @Mock ImagingUploadRegistrationService registration;
    @Mock ImagingDicomStudyValidator validator;
    @Mock ImagingArchivePort archive;
    @Mock ImagingIngestOperationPersistenceService operations;
    @Mock ImagingStudyPersistenceService studies;
    @Mock ImagingAuditPersistenceService audit;
    @Mock ImagingReconciliationService reconciliation;

    @Test
    void hashesAndRegistersExactMultipartEntityBeforeParsingAndQuarantinesUnknownIdentity() throws Exception {
        byte[] rawBody = ("--otcms-boundary\r\nContent-Disposition: form-data; name=\"files\"; "
                + "filename=\"slice.dcm\"\r\nContent-Type: application/dicom\r\n\r\n"
                + "synthetic-dicom-body\r\n--otcms-boundary--\r\n").getBytes(StandardCharsets.ISO_8859_1);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawBody));
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();
        ImagingOperationRecord processing = operation(operationId, ImagingOperationState.PROCESSING, now);
        ImagingOperationRecord quarantined = operation(operationId, ImagingOperationState.QUARANTINED, now);
        var validated = org.mockito.Mockito.mock(ValidatedDicomStudy.class);
        when(validated.summary()).thenReturn(new ImagingDicomStudyValidation(ImagingVerificationStatus.UNKNOWN,
                "1.2.826.0.1.3680043.10.543.1", "MR", 1, 1,
                List.of("PATIENT_ISSUER_MISSING", "ACCESSION_ISSUER_MISSING")));
        when(registration.register(eq(WORK.actorAccountId()), eq(WORK.requestId()), eq(WORK.attemptId()),
                eq("upload-key"), eq(digest), eq((long) rawBody.length), eq(WORK)))
                .thenReturn(new ImagingUploadRegistrationService.Registration(processing, true));
        when(validator.validateAndExtract(eq("multipart/form-data; boundary=otcms-boundary"),
                eq((long) rawBody.length), any(), eq(WORK))).thenReturn(validated);
        when(operations.findById(operationId)).thenReturn(Optional.of(quarantined));

        ImagingProperties settings = new ImagingProperties();
        settings.setEnabled(true);
        settings.setMaxUploadBytes(1024 * 1024);
        settings.setMaxUploadFiles(10);
        settings.setMaxConcurrentUploads(1);
        var orchestrator = new ImagingUploadOrchestrator(settings, new ImagingRequestBodySpooler(), registration,
                validator, archive, operations, studies, audit, reconciliation);
        var command = new ImagingUploadCommand(WORK.actorAccountId(), WORK.requestId(), WORK.attemptId(),
                "upload-key", "multipart/form-data; boundary=otcms-boundary", rawBody.length,
                new ByteArrayInputStream(rawBody));

        var result = orchestrator.upload(command, WORK);

        assertEquals(ImagingOperationState.QUARANTINED, result.state());
        InOrder order = inOrder(registration, validator);
        order.verify(registration).register(WORK.actorAccountId(), WORK.requestId(), WORK.attemptId(),
                "upload-key", digest, rawBody.length, WORK);
        order.verify(validator).validateAndExtract(eq(command.contentType()), anyLong(), any(), eq(WORK));
        verify(operations).recordValidation(operationId, validated.summary());
        verify(operations).transition(eq(operationId), eq(processing.version()),
                eq(ImagingOperationState.QUARANTINED), eq("IDENTITY_UNKNOWN"), any());
        verify(reconciliation).openOperation(operationId, "INGEST", ImagingReconciliationClassification.ORPHANED,
                "IDENTITY_UNKNOWN");
        verify(archive, never()).storeInstance(any());
        verify(studies, never()).persistConfirmedStudy(any(), any(), any(), any());
    }

    @Test
    void sendsUncertainArchiveOutcomeToReconciliationWithoutCreatingStudyLink() throws Exception {
        var setup = confirmedUpload();
        when(archive.storeInstance(Path.of("safe-generated-instance.dcm")))
                .thenThrow(new ImagingOperationException(ImagingOperationOutcome.RECONCILIATION_REQUIRED));

        var result = upload(setup);

        assertEquals(ImagingOperationState.RECONCILIATION_REQUIRED, result.state());
        verify(operations).transition(eq(setup.operation().id()), eq(setup.operation().version()),
                eq(ImagingOperationState.RECONCILIATION_REQUIRED),
                eq("ARCHIVE_OR_DATABASE_OUTCOME_UNCERTAIN"), any());
        verify(reconciliation).openOperation(setup.operation().id(), "INGEST",
                "ARCHIVE_OR_DATABASE_OUTCOME_UNCERTAIN");
        verify(studies, never()).persistConfirmedStudy(any(), any(), any(), any());
    }

    @Test
    void reconcilesWhenArchiveAcceptedAnInstanceButStudyPersistenceFailed() throws Exception {
        var setup = confirmedUpload();
        when(setup.validated().objects()).thenReturn(List.of(new ValidatedDicomStudy.DicomObjectReference(
                "1.2.826.0.1.3680043.10.543.1.1", "1.2.826.0.1.3680043.10.543.1.1.1")));
        when(archive.storeInstance(Path.of("safe-generated-instance.dcm")))
                .thenReturn(new ArchiveInstanceReceipt("archive-instance", "archive-series", "archive-study"));
        when(studies.persistConfirmedStudy(eq(setup.operation()), any(), any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        var result = upload(setup);

        assertEquals(ImagingOperationState.RECONCILIATION_REQUIRED, result.state());
        verify(archive).storeInstance(Path.of("safe-generated-instance.dcm"));
        verify(studies).persistConfirmedStudy(eq(setup.operation()), any(), any(), any());
        verify(operations).transition(eq(setup.operation().id()), eq(setup.operation().version()),
                eq(ImagingOperationState.RECONCILIATION_REQUIRED),
                eq("ARCHIVE_OR_DATABASE_OUTCOME_UNCERTAIN"), any());
        verify(reconciliation).openOperation(setup.operation().id(), "INGEST",
                "ARCHIVE_OR_DATABASE_OUTCOME_UNCERTAIN");
    }

    private ConfirmedUpload confirmedUpload() throws Exception {
        byte[] rawBody = "synthetic confirmed upload".getBytes(StandardCharsets.UTF_8);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawBody));
        UUID operationId = UUID.randomUUID();
        Instant now = Instant.now();
        ImagingOperationRecord processing = operation(operationId, ImagingOperationState.PROCESSING, now);
        var validated = org.mockito.Mockito.mock(ValidatedDicomStudy.class);
        var summary = new ImagingDicomStudyValidation(ImagingVerificationStatus.CONFIRMED_MATCH,
                "1.2.826.0.1.3680043.10.543.1", "MR", 1, 1, List.of());
        when(validated.summary()).thenReturn(summary);
        when(validated.instanceFiles()).thenReturn(List.of(Path.of("safe-generated-instance.dcm")));
        when(registration.register(eq(WORK.actorAccountId()), eq(WORK.requestId()), eq(WORK.attemptId()),
                eq("upload-key"), eq(digest), eq((long) rawBody.length), eq(WORK)))
                .thenReturn(new ImagingUploadRegistrationService.Registration(processing, true));
        when(validator.validateAndExtract(eq("application/zip"), eq((long) rawBody.length), any(), eq(WORK)))
                .thenReturn(validated);
        when(operations.findById(operationId)).thenReturn(Optional.of(
                operation(operationId, ImagingOperationState.RECONCILIATION_REQUIRED, now)));
        return new ConfirmedUpload(rawBody, processing, validated);
    }

    private fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationStatus upload(ConfirmedUpload setup) {
        ImagingProperties settings = new ImagingProperties();
        settings.setEnabled(true);
        settings.setMaxUploadBytes(1024 * 1024);
        settings.setMaxUploadFiles(10);
        settings.setMaxConcurrentUploads(1);
        var orchestrator = new ImagingUploadOrchestrator(settings, new ImagingRequestBodySpooler(), registration,
                validator, archive, operations, studies, audit, reconciliation);
        var command = new ImagingUploadCommand(WORK.actorAccountId(), WORK.requestId(), WORK.attemptId(),
                "upload-key", "application/zip", setup.rawBody().length,
                new ByteArrayInputStream(setup.rawBody()));
        return orchestrator.upload(command, WORK);
    }

    private record ConfirmedUpload(byte[] rawBody, ImagingOperationRecord operation,
            ValidatedDicomStudy validated) { }

    private static ImagingOperationRecord operation(UUID id, ImagingOperationState state, Instant now) {
        return new ImagingOperationRecord(id, null, WORK.requestId(), WORK.attemptId(), WORK.visitId(),
                WORK.patientId(), WORK.actorAccountId(), ImagingOperationType.UPLOAD, state,
                "a".repeat(64), 1, null, null, 0, 0, ImagingVerificationStatus.NOT_EVALUATED,
                List.of(), null, null, 0, now, now);
    }
}
