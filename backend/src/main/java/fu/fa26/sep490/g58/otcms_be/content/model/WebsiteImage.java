package fu.fa26.sep490.g58.otcms_be.content.model;

import java.util.UUID;

/** Metadata of one website image. Bytes live in object storage at storageKey; storageKey and byteSize are null on legacy rows. */
public record WebsiteImage(UUID id, String storageKey, String contentType, Long byteSize) {
    /** Image bytes read from object storage for the public image endpoint. */
    public record Content(String contentType, byte[] bytes) {
    }
}
