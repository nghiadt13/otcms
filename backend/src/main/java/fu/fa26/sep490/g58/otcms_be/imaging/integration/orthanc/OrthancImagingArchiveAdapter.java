package fu.fa26.sep490.g58.otcms_be.imaging.integration.orthanc;

import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Private REST adapter for Orthanc's instance ingest endpoint. */
@Component
public class OrthancImagingArchiveAdapter implements ImagingArchivePort {
    private static final int MAX_CONNECT_ATTEMPTS = 2;
    private static final Duration RETRY_DELAY = Duration.ofMillis(150);

    private final ImagingProperties settings;
    private volatile RestClient restClient;

    public OrthancImagingArchiveAdapter(ImagingProperties settings) {
        this.settings = settings;
    }

    @Override
    public ArchiveInstanceReceipt storeInstance(Path dicomFile) {
        Objects.requireNonNull(dicomFile, "dicomFile");
        RestClient client = client();
        for (int attempt = 1; attempt <= MAX_CONNECT_ATTEMPTS; attempt++) {
            try {
                JsonNode response = client.post()
                        .uri("/instances")
                        .contentType(MediaType.parseMediaType("application/dicom"))
                        .body(new FileSystemResource(dicomFile))
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, (request, result) -> {
                            throw new ImagingOperationException(ImagingOperationOutcome.FILE_INVALID);
                        })
                        .onStatus(HttpStatusCode::is5xxServerError, (request, result) -> {
                            throw new ImagingOperationException(ImagingOperationOutcome.RECONCILIATION_REQUIRED);
                        })
                        .body(JsonNode.class);
                return receipt(response);
            } catch (ImagingOperationException exception) {
                throw exception;
            } catch (ResourceAccessException exception) {
                if (attempt < MAX_CONNECT_ATTEMPTS && connectionWasNotEstablished(exception)) {
                    pauseBeforeRetry();
                    continue;
                }
                throw new ImagingOperationException(connectionWasNotEstablished(exception)
                        ? ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE
                        : ImagingOperationOutcome.RECONCILIATION_REQUIRED);
            } catch (RuntimeException exception) {
                throw new ImagingOperationException(ImagingOperationOutcome.RECONCILIATION_REQUIRED);
            }
        }
        throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
    }

    private RestClient client() {
        RestClient current = restClient;
        if (current != null) return current;
        synchronized (this) {
            if (restClient == null) restClient = buildClient();
            return restClient;
        }
    }

    private RestClient buildClient() {
        if (settings.getOrthancBaseUri() == null || !positive(settings.getOrthancConnectTimeout())
                || !positive(settings.getOrthancResponseTimeout())) {
            throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(settings.getOrthancConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(settings.getOrthancResponseTimeout());
        return RestClient.builder().baseUrl(settings.getOrthancBaseUri().toString())
                .requestFactory(requestFactory).build();
    }

    private static ArchiveInstanceReceipt receipt(JsonNode response) {
        if (response == null || !response.isObject()) {
            throw new ImagingOperationException(ImagingOperationOutcome.RECONCILIATION_REQUIRED);
        }
        String status = text(response, "Status");
        if (!"Success".equals(status) && !"AlreadyStored".equals(status)) {
            throw new ImagingOperationException(ImagingOperationOutcome.RECONCILIATION_REQUIRED);
        }
        try {
            return new ArchiveInstanceReceipt(text(response, "ID"), text(response, "ParentSeries"),
                    text(response, "ParentStudy"));
        } catch (IllegalArgumentException exception) {
            throw new ImagingOperationException(ImagingOperationOutcome.RECONCILIATION_REQUIRED);
        }
    }

    private static String text(JsonNode response, String name) {
        JsonNode node = response.get(name);
        return node == null || !node.isTextual() ? null : node.stringValue();
    }

    private static boolean connectionWasNotEstablished(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException) return true;
        }
        return false;
    }

    private static void pauseBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }
}
