package fu.fa26.sep490.g58.otcms_be.common.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/** Dev/test provider: objects are plain files under a root folder (content type is not kept). */
public final class LocalObjectStorageAdapter implements ObjectStoragePort {
    private final Path root;

    public LocalObjectStorageAdapter(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, String contentType, byte[] content) {
        Path file = resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not store object", exception);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(Files.readAllBytes(resolve(key)));
        } catch (NoSuchFileException exception) {
            return Optional.empty();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read object", exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not delete object", exception);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    private Path resolve(String key) {
        Path file = root.resolve(ObjectStoragePort.requireSafeKey(key)).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return file;
    }
}
