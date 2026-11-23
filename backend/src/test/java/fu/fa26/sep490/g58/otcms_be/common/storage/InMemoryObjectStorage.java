package fu.fa26.sep490.g58.otcms_be.common.storage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test double for {@link ObjectStoragePort}; keeps objects in a map. */
public final class InMemoryObjectStorage implements ObjectStoragePort {
    public final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    public final Map<String, String> contentTypes = new ConcurrentHashMap<>();

    @Override public void put(String key, String contentType, byte[] content) {
        ObjectStoragePort.requireSafeKey(key);
        objects.put(key, content.clone());
        contentTypes.put(key, contentType);
    }
    @Override public Optional<byte[]> get(String key) { return Optional.ofNullable(objects.get(key)).map(byte[]::clone); }
    @Override public void delete(String key) { objects.remove(key); contentTypes.remove(key); }
    @Override public boolean exists(String key) { return objects.containsKey(key); }
}
