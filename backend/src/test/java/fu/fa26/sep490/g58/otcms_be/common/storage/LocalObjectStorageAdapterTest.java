package fu.fa26.sep490.g58.otcms_be.common.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalObjectStorageAdapterTest {
    @TempDir Path root;

    @Test
    void putGetExistsDeleteRoundTrip() {
        var storage = new LocalObjectStorageAdapter(root);
        byte[] data = {1, 2, 3};
        storage.put("avatars/a/b.png", "image/png", data);
        assertTrue(storage.exists("avatars/a/b.png"));
        assertArrayEquals(data, storage.get("avatars/a/b.png").orElseThrow());
        storage.delete("avatars/a/b.png");
        assertFalse(storage.exists("avatars/a/b.png"));
        assertTrue(storage.get("avatars/a/b.png").isEmpty());
        storage.delete("avatars/a/b.png");
    }

    @Test
    void rejectsKeysThatEscapeTheRoot() {
        var storage = new LocalObjectStorageAdapter(root);
        for (String key : new String[] {"../x", "/abs", "a/../../x", "a\b", "a//b", ""}) {
            assertThrows(IllegalArgumentException.class, () -> storage.put(key, "image/png", new byte[] {1}), key);
        }
    }
}
