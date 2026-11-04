package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalMedicalDocumentType;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.Instant;
import java.util.*;

public interface ClinicalMedicalDocumentService {
    DocumentView upload(AuthenticatedAccount actor, UUID visitId, ClinicalMedicalDocumentType type, String title, DocumentFile file);
    DocumentView update(AuthenticatedAccount actor, UUID visitId, UUID documentId, Long version, String title, DocumentFile replacement);
    DocumentView release(AuthenticatedAccount actor, UUID visitId, UUID documentId, Long version);
    List<DocumentView> list(AuthenticatedAccount actor, UUID visitId);
    DocumentView get(AuthenticatedAccount actor, UUID documentId);
    DocumentContent content(AuthenticatedAccount actor, UUID documentId);

    record DocumentFile(String originalFilename, String mediaType, byte[] bytes) {
        public DocumentFile { bytes = bytes == null ? null : bytes.clone(); }
        @Override public byte[] bytes() { return bytes == null ? null : bytes.clone(); }
        @Override public String toString() { return "DocumentFile[redacted]"; }
    }
    record DocumentView(UUID id, UUID visitId, UUID patientProfileId, ClinicalMedicalDocumentType documentType,
            String title, String originalFilename, String mediaType, long byteSize, boolean released,
            Instant releasedAt, long version, Instant createdAt, Instant updatedAt) {
        @Override public String toString() { return "DocumentView[redacted]"; }
    }
    record DocumentContent(DocumentView document, byte[] bytes) {
        public DocumentContent { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
        @Override public String toString() { return "DocumentContent[redacted]"; }
    }
}
