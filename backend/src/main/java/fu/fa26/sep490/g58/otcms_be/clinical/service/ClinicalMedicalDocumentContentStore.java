package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.common.storage.ObjectStoragePort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Medical document bytes (PDF/images) in object storage. The key is derived from the document row
 * ({@code medical-documents/<visitId>/<documentId>}), so no key column is needed: a document keeps its key for life.
 */
@Component
public class ClinicalMedicalDocumentContentStore {
    private final ObjectStoragePort storage;

    public ClinicalMedicalDocumentContentStore(ObjectStoragePort storage) {
        this.storage = storage;
    }

    /** Writes or overwrites the content; returns false when the storage is unavailable. */
    public boolean put(UUID visitId, UUID documentId, String mediaType, byte[] content) {
        try {
            storage.put(key(visitId, documentId), mediaType, content);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Empty when the object is missing or the storage is unavailable. */
    public Optional<byte[]> get(UUID visitId, UUID documentId) {
        try {
            return storage.get(key(visitId, documentId));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    static String key(UUID visitId, UUID documentId) {
        return "medical-documents/" + visitId + "/" + documentId;
    }
}
