package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import java.io.IOException;
import java.io.InputStream;

/** Reopenable HTTP upload content, allowing payload fingerprinting before an idempotency claim. */
public record DiagnosticReportUpload(InputStreamSupplier content, long contentLength, String contentType) {
    @FunctionalInterface
    public interface InputStreamSupplier {
        InputStream open() throws IOException;
    }
}
