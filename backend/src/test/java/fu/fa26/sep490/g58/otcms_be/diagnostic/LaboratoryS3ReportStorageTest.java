package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportContentValidator;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.minio.MinioReportStorageAdapter;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.ResponseInputStream;

class LaboratoryS3ReportStorageTest {
    @Test
    void storesPrivateReportsAndValidatesScopedReferences() {
        S3Client client = mock(S3Client.class);
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());
        ReportContentValidator validator = bytes -> ReportValidationStatus.VALIDATED;
        ResponseInputStream<GetObjectResponse> reportInput = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream(new byte[] {1, 2, 3}), () -> { }));
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(reportInput);
        MinioReportStorageAdapter storage =
                new MinioReportStorageAdapter(client, "private", validator);

        UUID requestId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        String studyRef = "study-123";
        byte[] reportBytes = {1, 2, 3};
        var stored = storage.storePrivateReport(
                requestId,
                visitId,
                studyRef,
                1,
                new ByteArrayInputStream(reportBytes),
                reportBytes.length);

        assertEquals(requestId, stored.requestId());
        assertEquals(visitId, stored.visitId());
        assertEquals(studyRef, stored.studyRef());
        assertEquals(ReportValidationStatus.PENDING, stored.validationStatus());
        assertEquals(ReportValidationStatus.VALIDATED,
                storage.validateStoredReport(stored.reportId(), stored.storageRef()));
        verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThrows(IllegalArgumentException.class,
                () -> storage.validateStoredReport(UUID.randomUUID(), stored.storageRef()));
        assertThrows(IllegalArgumentException.class,
                () -> storage.storePrivateReport(
                        requestId,
                        visitId,
                        studyRef,
                        1,
                        new ByteArrayInputStream(reportBytes),
                        reportBytes.length + 1));
        assertThrows(IllegalArgumentException.class,
                () -> storage.openReleasedReport(stored.reportId(), "invalid-ref", UUID.randomUUID()));
    }
}
