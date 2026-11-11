package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportStoragePort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticReadPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticReportUpload;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticFindingsServiceImpl;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class DiagnosticReportChecksumTest {
    @Test
    @SuppressWarnings("unchecked")
    void savesChecksumOfStoredPdfBytes() throws Exception {
        var requests = mock(DiagnosticRequestJpaRepository.class);
        var attempts = mock(DiagnosticAttemptJpaRepository.class);
        var findings = mock(DiagnosticFindingsVersionJpaRepository.class);
        var reports = mock(DiagnosticReportJpaRepository.class);
        var idempotency = mock(DiagnosticIdempotencyRecordJpaRepository.class);
        var visitPorts = (ObjectProvider<VisitDiagnosticReadPort>) mock(ObjectProvider.class);
        var storagePorts = (ObjectProvider<ReportStoragePort>) mock(ObjectProvider.class);
        var visitPort = mock(VisitDiagnosticReadPort.class);
        var storage = mock(ReportStoragePort.class);
        var request = mock(DiagnosticRequestJpaEntity.class);
        var attempt = mock(DiagnosticAttemptJpaEntity.class);
        var record = mock(DiagnosticIdempotencyRecordJpaEntity.class);
        UUID requestId = UUID.randomUUID(), visitId = UUID.randomUUID(), patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID(), attemptId = UUID.randomUUID(), reportId = UUID.randomUUID();
        byte[] bytes = "%PDF-1.7 sample".getBytes(StandardCharsets.US_ASCII);
        when(requests.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(request.getId()).thenReturn(requestId);
        when(request.getVisitId()).thenReturn(visitId);
        when(request.getPatientId()).thenReturn(patientId);
        when(request.getOrderingDoctorId()).thenReturn(doctorId);
        when(request.getCurrentAttemptId()).thenReturn(attemptId);
        when(request.getStatus()).thenReturn(DiagnosticRequestStatus.REVIEWED);
        when(attempts.findByRequestIdAndId(requestId, attemptId)).thenReturn(Optional.of(attempt));
        when(attempt.getStudyRef()).thenReturn("study");
        when(visitPorts.getIfAvailable()).thenReturn(visitPort);
        when(visitPort.requireImagingOrderEligibility(visitId, doctorId))
                .thenReturn(new VisitEvidence(visitId, patientId, doctorId, true, true, true));
        when(idempotency.claimActorOperation(any(), any(), any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        when(idempotency.findActorOperation(any(), any(), any(), anyString(), anyString())).thenReturn(Optional.of(record));
        when(storagePorts.getIfAvailable()).thenReturn(storage);
        when(storage.storePrivateReport(eq(requestId), eq(visitId), eq("study"), eq(1), any(), eq((long) bytes.length)))
                .thenAnswer(invocation -> {
                    ((java.io.InputStream) invocation.getArgument(4)).readAllBytes();
                    return new StoredReport(reportId, requestId, visitId, "study", 1,
                            "private-report:" + reportId, ReportValidationStatus.PENDING);
                });
        when(storage.validateStoredReport(eq(reportId), anyString())).thenReturn(ReportValidationStatus.PENDING);
        var service = new DiagnosticFindingsServiceImpl(requests, attempts, findings, reports, idempotency,
                mock(DiagnosticAuditEventJpaRepository.class), mock(DiagnosticOutboxJpaRepository.class),
                mock(DiagnosticAuthorizationPolicy.class), visitPorts, storagePorts);
        service.uploadReport(DiagnosticActor.user(doctorId, AccountRole.DOCTOR), requestId, "key", 0,
                new DiagnosticReportUpload(() -> new ByteArrayInputStream(bytes), bytes.length, "application/pdf"),
                "correlation");
        var saved = ArgumentCaptor.forClass(DiagnosticReportJpaEntity.class);
        verify(reports).saveAndFlush(saved.capture());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                saved.getValue().getContentSha256());
    }
}
