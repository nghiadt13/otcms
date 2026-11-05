package fu.fa26.sep490.g58.otcms_be.patient.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse.FieldError;
import java.util.List;

public class MedicalHistoryException extends RuntimeException {
    private final List<FieldError> fieldErrors;
    public MedicalHistoryException(String code) { this(code, List.of()); }
    public MedicalHistoryException(String code, List<FieldError> fields) {
        super(code); this.fieldErrors = List.copyOf(fields);
    }
    public List<FieldError> fieldErrors() { return fieldErrors; }
}
