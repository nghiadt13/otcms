package fu.fa26.sep490.g58.otcms_be.review.exception;

import fu.fa26.sep490.g58.otcms_be.review.model.dto.ReviewDtos.FieldError;
import java.util.List;

public class ReviewOperationException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<FieldError> fieldErrors;
    public ReviewOperationException(int status, String code) { this(status, code, List.of()); }
    public ReviewOperationException(int status, String code, List<FieldError> fieldErrors) {
        super(code); this.status = status; this.code = code; this.fieldErrors = List.copyOf(fieldErrors);
    }
    public int status() { return status; }
    public String code() { return code; }
    public List<FieldError> fieldErrors() { return fieldErrors; }
}
