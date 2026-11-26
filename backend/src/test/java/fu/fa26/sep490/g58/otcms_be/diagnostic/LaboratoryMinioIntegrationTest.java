package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ReportStoragePort;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@SpringBootTest(properties = "app.diagnostic.report-storage.provider=minio")
@LocalPostgresTest
class LaboratoryMinioIntegrationTest {
    private static final String STORAGE_REF_PREFIX = "minio-private-report:";

    @Autowired ReportStoragePort storage;
    @Autowired S3Client client;
    @Value("${app.diagnostic.report-storage.minio.bucket}") String bucket;

    @Test
    void storesReadsAndRemovesSyntheticReport() throws Exception {
        byte[] bytes = pdf();
        UUID requestId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        var report = storage.storePrivateReport(
                requestId, visitId, "synthetic-study", 1,
                new java.io.ByteArrayInputStream(bytes), bytes.length);

        try {
            try (var input = storage.openReleasedReport(report.reportId(), report.storageRef(), actorId)) {
                assertArrayEquals(bytes, input.readAllBytes());
            }
            assertThrows(IllegalArgumentException.class,
                    () -> storage.openReleasedReport(UUID.randomUUID(), report.storageRef(), actorId));
        } finally {
            String objectKey = report.storageRef().substring(STORAGE_REF_PREFIX.length());
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
        }
    }

    private static byte[] pdf() throws IOException {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }
}
