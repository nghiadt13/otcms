package fu.fa26.sep490.g58.otcms_be.patient.exception;

public class PatientProfileException extends RuntimeException {
    private final java.util.List<fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse.FieldError> fieldErrors;
    public PatientProfileException(String code) { this(code, java.util.List.of()); }
    public PatientProfileException(String code,
            java.util.List<fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse.FieldError> fieldErrors) {
        super(code);
        this.fieldErrors = java.util.List.copyOf(fieldErrors);
    }
    public java.util.List<fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse.FieldError> fieldErrors() { return fieldErrors; }
}
