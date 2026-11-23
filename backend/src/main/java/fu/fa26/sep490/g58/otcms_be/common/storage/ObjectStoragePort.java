package fu.fa26.sep490.g58.otcms_be.common.storage;

import java.util.Optional;

/**
 * Private object storage for user media (avatars, staff photos, website images, medical documents).
 * Features depend on this port only; the provider (local folder or Supabase Storage) is chosen by
 * {@code app.storage.provider}. Keys look like {@code avatars/<accountId>/<uuid>.png}.
 */
public interface ObjectStoragePort {
    /** Writes (or overwrites) one object. */
    void put(String key, String contentType, byte[] content);

    /** Reads one object, empty when the key does not exist. */
    Optional<byte[]> get(String key);

    /** Deletes one object; a missing key is not an error. */
    void delete(String key);

    boolean exists(String key);

    /** Rejects keys that could escape the bucket or storage root. */
    static String requireSafeKey(String key) {
        if (key == null || key.isBlank() || key.length() > 300 || key.startsWith("/") || key.contains("\\")
                || key.contains("..") || key.contains("//") || key.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return key;
    }
}
