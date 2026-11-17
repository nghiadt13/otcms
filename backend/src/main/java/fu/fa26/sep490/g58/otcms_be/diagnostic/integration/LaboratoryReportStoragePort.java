package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import java.io.InputStream;
import java.util.UUID;

/** Private partner report storage. Authorization belongs to the calling service. */
public interface LaboratoryReportStoragePort {
    Stored store(UUID orderId, byte[] contents, String contentType);
    InputStream open(UUID orderId, UUID reportId, String storageRef);
    record Stored(UUID reportId, String storageRef, String sha256, String contentType) { }
}
