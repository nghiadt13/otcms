package fu.fa26.sep490.g58.otcms_be.imaging.integration.orthanc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OrthancImagingArchiveAdapterTest {
    @Test
    void streamsDicomInstanceToPrivateOrthancRouteAndReturnsOpaqueReferences() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<Integer> bodySize = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/instances", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            bodySize.set(exchange.getRequestBody().readAllBytes().length);
            byte[] json = "{\"ID\":\"instance-ref\",\"ParentSeries\":\"series-ref\",\"ParentStudy\":\"study-ref\",\"Status\":\"Success\"}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            exchange.getResponseBody().write(json);
            exchange.close();
        });
        server.start();
        Path dicom = Files.createTempFile("orthanc-adapter-test-", ".dcm");
        try {
            Files.write(dicom, new byte[] {1, 2, 3, 4, 5});
            ImagingProperties settings = settings(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));

            ArchiveInstanceReceipt result = new OrthancImagingArchiveAdapter(settings).storeInstance(dicom);

            assertEquals("POST", method.get());
            assertEquals("/instances", path.get());
            assertEquals("application/dicom", contentType.get());
            assertEquals(5, bodySize.get());
            assertEquals("instance-ref", result.archiveInstanceRef());
            assertEquals("series-ref", result.archiveSeriesRef());
            assertEquals("study-ref", result.archiveStudyRef());
        } finally {
            server.stop(0);
            Files.deleteIfExists(dicom);
        }
    }

    @Test
    void mapsAmbiguousOrthancServerFailureToReconciliationWithoutProviderBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/instances", exchange -> {
            byte[] response = "provider-internal-detail".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        Path dicom = Files.createTempFile("orthanc-adapter-test-", ".dcm");
        try {
            ImagingOperationException error = assertThrows(ImagingOperationException.class,
                    () -> new OrthancImagingArchiveAdapter(settings(
                            URI.create("http://127.0.0.1:" + server.getAddress().getPort())))
                            .storeInstance(dicom));
            assertEquals(ImagingOperationOutcome.RECONCILIATION_REQUIRED, error.outcome());
            assertEquals("RECONCILIATION_REQUIRED", error.getMessage());
        } finally {
            server.stop(0);
            Files.deleteIfExists(dicom);
        }
    }

    @Test
    void failsClosedWhenArchiveEndpointIsNotConfigured() throws Exception {
        Path dicom = Files.createTempFile("orthanc-adapter-test-", ".dcm");
        try {
            ImagingProperties settings = new ImagingProperties();
            ImagingOperationException error = assertThrows(ImagingOperationException.class,
                    () -> new OrthancImagingArchiveAdapter(settings).storeInstance(dicom));
            assertEquals(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE, error.outcome());
        } finally {
            Files.deleteIfExists(dicom);
        }
    }

    private static ImagingProperties settings(URI baseUri) {
        ImagingProperties settings = new ImagingProperties();
        settings.setOrthancBaseUri(baseUri);
        settings.setOrthancConnectTimeout(Duration.ofSeconds(1));
        settings.setOrthancResponseTimeout(Duration.ofSeconds(2));
        return settings;
    }
}
