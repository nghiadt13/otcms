package fu.fa26.sep490.g58.otcms_be.clinical.validation;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalMedicalDocumentType;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentService.DocumentFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ClinicalMedicalDocumentFileValidator {
    /** Matches the existing application.yml multipart 2MB limit. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    public DocumentFile validate(ClinicalMedicalDocumentType type, DocumentFile file) {
        if (type == null || file == null) throw unsuitable();
        byte[] bytes = file.bytes();
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES || file.mediaType() == null) throw unsuitable();
        String media = file.mediaType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (type == ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT && !media.equals("application/pdf")) throw unsuitable();
        boolean valid = switch (media) {
            case "application/pdf" -> pdf(bytes);
            case "image/jpeg" -> bytes.length >= 8 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                    && (bytes[2] & 255) == 255 && (bytes[bytes.length - 2] & 255) == 255 && (bytes[bytes.length - 1] & 255) == 217;
            case "image/png" -> bytes.length >= 20
                    && Arrays.equals(Arrays.copyOf(bytes, 8), new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})
                    && Arrays.equals(Arrays.copyOfRange(bytes, bytes.length - 8, bytes.length - 4), new byte[]{73, 69, 78, 68});
            default -> false;
        };
        if (!valid) throw unsuitable();
        String filename = file.originalFilename();
        if (filename == null) throw unsuitable();
        filename = filename.replace('\\', '/'); filename = filename.substring(filename.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "").trim();
        if (filename.isBlank() || filename.equals(".") || filename.equals("..") || filename.length() > 255) throw unsuitable();
        return new DocumentFile(filename, media, bytes);
    }
    private boolean pdf(byte[] bytes) {
        if (bytes.length < 32) return false;
        String header = new String(bytes, 0, Math.min(8, bytes.length), StandardCharsets.US_ASCII);
        if (!header.matches("%PDF-[12]\\.[0-9]")) return false;
        String body = new String(bytes, StandardCharsets.ISO_8859_1);
        String tail = body.substring(Math.max(0, body.length() - 1024)).stripTrailing();
        return body.contains(" obj") && body.contains("endobj") && body.contains("startxref") && tail.endsWith("%%EOF");
    }
    private static ClinicalOperationException unsuitable() { return new ClinicalOperationException("MSG-47"); }
}
