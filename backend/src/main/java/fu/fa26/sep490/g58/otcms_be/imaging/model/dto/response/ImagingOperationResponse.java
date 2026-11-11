package fu.fa26.sep490.g58.otcms_be.imaging.model.dto.response;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationStatus;
import java.time.Instant;
import java.util.UUID;

/** HTTP-safe operation projection; intentionally excludes owner, DICOM and archive identifiers. */
public record ImagingOperationResponse(UUID operationId, String state, Instant createdAt,
        Instant updatedAt, String statusUrl, OperationError error, Integer instanceCount) {
    public static ImagingOperationResponse from(ImagingOperationStatus status) {
        OperationError error = status.safeOutcomeCode() == null ? null
                : new OperationError(status.safeOutcomeCode(), "Imaging operation needs attention");
        return new ImagingOperationResponse(status.operationId(), status.state().name(), status.createdAt(),
                status.updatedAt(), "/api/v1/imaging/ingest-operations/" + status.operationId(), error,
                status.instanceCount());
    }

    public record OperationError(String code, String message) { }
}
