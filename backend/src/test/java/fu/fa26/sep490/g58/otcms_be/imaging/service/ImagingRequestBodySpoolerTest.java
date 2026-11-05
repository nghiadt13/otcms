package fu.fa26.sep490.g58.otcms_be.imaging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingRequestBodySpooler;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class ImagingRequestBodySpoolerTest {
    private final ImagingRequestBodySpooler spooler = new ImagingRequestBodySpooler();

    @Test
    void hashesExactRawEntityAndDeletesSpoolOnClose() throws Exception {
        byte[] entity = "--boundary\r\nContent-Disposition: form-data\r\n\r\nbytes\r\n--boundary--\r\n"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(entity));
        var spool = spooler.spool(new ByteArrayInputStream(entity), entity.length, entity.length);
        var path = spool.path();
        assertEquals(expected, spool.sha256());
        assertEquals(entity.length, spool.size());
        assertEquals(java.util.Arrays.toString(entity), java.util.Arrays.toString(Files.readAllBytes(path)));
        spool.close();
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(path));
    }

    @Test
    void rejectsOversizeAndMismatchedDeclaredLength() {
        assertOutcome(ImagingOperationOutcome.FILE_INVALID,
                () -> spooler.spool(new ByteArrayInputStream(new byte[5]), 5, 4));
        assertOutcome(ImagingOperationOutcome.FILE_INVALID,
                () -> spooler.spool(new ByteArrayInputStream(new byte[2]), 3, 4));
    }

    private static void assertOutcome(ImagingOperationOutcome outcome, Runnable action) {
        ImagingOperationException error = assertThrows(ImagingOperationException.class, action::run);
        assertEquals(outcome, error.outcome());
    }
}
