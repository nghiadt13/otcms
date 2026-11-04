package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService.ImagingRequestCommand;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Versioned, length-prefixed UTF-8 encoding; never log canonical medical request contents. */
public final class ClinicalImagingRequestFingerprint {
    private ClinicalImagingRequestFingerprint() { }

    public static ImagingRequestCommand validated(ImagingRequestCommand command) {
        if (command == null || command.visitId() == null || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank() || command.idempotencyKey().length() > 128
                || command.orders() == null || command.orders().isEmpty()) throw new ClinicalOperationException("MSG-39");
        if (command.doctorProfileId() == null) throw new ClinicalOperationException("MSG-40");
        for (var order : command.orders()) {
            if (order == null || order.serviceId() == null || order.clinicalReason() == null
                    || order.clinicalReason().isBlank() || order.bodyArea() == null || order.bodyArea().isBlank()
                    || order.bodyArea().length() > 255) throw new ClinicalOperationException("MSG-39");
        }
        return new ImagingRequestCommand(command.visitId(), command.doctorProfileId(),
                command.idempotencyKey().trim(), List.copyOf(command.orders()));
    }

    public static String fingerprint(ImagingRequestCommand command) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            field(output, "clinical-imaging-request-v1");
            field(output, command.doctorProfileId().toString());
            field(output, command.visitId().toString());
            output.writeInt(command.orders().size());
            for (var order : command.orders()) {
                field(output, order.serviceId().toString());
                field(output, order.clinicalReason());
                field(output, order.bodyArea());
                field(output, order.instructions());
            }
            output.flush();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required request digest is unavailable", impossible);
        }
    }

    private static void field(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
        } else {
            byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
            output.writeInt(encoded.length);
            output.write(encoded);
        }
    }
}
