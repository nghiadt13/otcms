package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class LocalS3ReportStorageAdapterTest {
    private final S3Client client = mock(S3Client.class);
    private final LocalS3ReportStorageAdapter adapter = new LocalS3ReportStorageAdapter(client,
            "private-reports", content -> ReportValidationStatus.PENDING);

    @Test
    void uploadStaysPendingAndUsesAnOpaqueObjectKey() {
        byte[] pdf = "%PDF-test".getBytes(StandardCharsets.UTF_8);
        var report = adapter.storePrivateReport(UUID.randomUUID(), UUID.randomUUID(), "study-secret", 1,
                new ByteArrayInputStream(pdf), pdf.length);
        assertEquals(ReportValidationStatus.PENDING, report.validationStatus());
        assertEquals(ReportValidationStatus.PENDING, adapter.validateStoredReport(report.reportId()));
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertEquals("private-reports", request.getValue().bucket());
        assertEquals("diagnostic-reports/" + report.reportId() + ".pdf", request.getValue().key());
    }

    @Test
    void mismatchedLengthDoesNotUpload() {
        assertThrows(IllegalArgumentException.class, () -> adapter.storePrivateReport(UUID.randomUUID(),
                UUID.randomUUID(), "study", 1, new ByteArrayInputStream(new byte[] {1, 2}), 1));
        verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void pendingReportCannotBeOpened() {
        assertThrows(IllegalStateException.class, () -> adapter.openReleasedReport(UUID.randomUUID(), UUID.randomUUID()));
    }
}
