package fu.fa26.sep490.g58.otcms_be.patient.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse.FieldError;
import java.util.List;

public class MyPatientProfileException extends RuntimeException {
    public enum Reason { INVALID_INPUT, ACCESS_DENIED, NOT_FOUND, STALE_VERSION, ALREADY_EXISTS, DUPLICATE }
    private final Reason reason;
    private final List<FieldError> fields;
    public MyPatientProfileException(Reason reason) { this(reason, List.of()); }
    public MyPatientProfileException(Reason reason, List<FieldError> fields) {
        super(reason.name()); this.reason = reason; this.fields = List.copyOf(fields);
    }
    public Reason reason() { return reason; }
    public List<FieldError> fields() { return fields; }
}
