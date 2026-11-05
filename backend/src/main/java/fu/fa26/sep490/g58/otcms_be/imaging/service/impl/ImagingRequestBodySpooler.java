package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Spools and hashes the exact raw HTTP entity before any multipart or DICOM parsing. */
@Component
public class ImagingRequestBodySpooler {
    public Spool spool(InputStream input, long declaredLength, long maximumBytes) {
        if (declaredLength < -1 || maximumBytes < 1 || declaredLength > maximumBytes) {
            throw new ImagingOperationException(ImagingOperationOutcome.FILE_INVALID);
        }
        Path file = null;
        try {
            file = Files.createTempFile("otcms-imaging-request-", ".body");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long count = 0;
            byte[] buffer = new byte[32 * 1024];
            try (OutputStream output = Files.newOutputStream(file)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    count += read;
                    if (count > maximumBytes) {
                        throw new ImagingOperationException(ImagingOperationOutcome.FILE_INVALID);
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            if (declaredLength >= 0 && count != declaredLength) {
                throw new ImagingOperationException(ImagingOperationOutcome.FILE_INVALID);
            }
            return new Spool(file, count, HexFormat.of().formatHex(digest.digest()));
        } catch (ImagingOperationException exception) {
            delete(file);
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            delete(file);
            throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
    }

    private static void delete(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); } catch (IOException ignored) { }
    }

    public static final class Spool implements AutoCloseable {
        private final Path path;
        private final long size;
        private final String sha256;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Spool(Path path, long size, String sha256) {
            this.path = path;
            this.size = size;
            this.sha256 = sha256;
        }

        public Path path() { return path; }
        public long size() { return size; }
        public String sha256() { return sha256; }

        @Override public void close() {
            if (!closed.compareAndSet(false, true)) return;
            delete(path);
        }
    }
}
