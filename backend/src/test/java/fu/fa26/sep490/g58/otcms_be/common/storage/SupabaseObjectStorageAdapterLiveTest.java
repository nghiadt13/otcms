package fu.fa26.sep490.g58.otcms_be.common.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Round-trip against a real Supabase Storage bucket. Skipped unless every APP_STORAGE_SUPABASE_S3_*
 * variable is set and APP_STORAGE_PROVIDER is supabase-storage, so CI and offline runs stay green.
 */
class SupabaseObjectStorageAdapterLiveTest {

    @Test
    void putGetExistsDeleteRoundTrip() {
        String endpoint = System.getenv("APP_STORAGE_SUPABASE_S3_ENDPOINT");
        String region = System.getenv("APP_STORAGE_SUPABASE_S3_REGION");
        String accessKey = System.getenv("APP_STORAGE_SUPABASE_S3_ACCESS_KEY");
        String secretKey = System.getenv("APP_STORAGE_SUPABASE_S3_SECRET_KEY");
        String bucket = System.getenv("APP_STORAGE_BUCKET");
        assumeTrue("supabase-storage".equals(System.getenv("APP_STORAGE_PROVIDER")));
        assumeTrue(isSet(endpoint) && isSet(region) && isSet(accessKey) && isSet(secretKey) && isSet(bucket));

        String key = "smoke-test/" + UUID.randomUUID() + ".txt";
        byte[] payload = "otcms storage smoke test".getBytes(StandardCharsets.UTF_8);
        try (SupabaseObjectStorageAdapter adapter =
                new SupabaseObjectStorageAdapter(URI.create(endpoint), region, accessKey, secretKey, bucket)) {
            adapter.put(key, "text/plain", payload);
            try {
                assertTrue(adapter.exists(key));
                assertArrayEquals(payload, adapter.get(key).orElseThrow());
            } finally {
                adapter.delete(key);
            }
            assertFalse(adapter.exists(key));
            assertEquals(java.util.Optional.empty(), adapter.get(key));
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
