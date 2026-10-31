package fu.fa26.sep490.g58.otcms_be.auth.model.dto.response;


/** Validation detail safe for clients; never contains submitted secret values. */
public class ValidationError {
    private final String field;
    private final String message;
    public ValidationError(String field, String message) {
        this.field = field;
        this.message = message;
    }
    public String getField() { return field; }
    public String field() { return field; }
    public String getMessage() { return message; }
    public String message() { return message; }
}
