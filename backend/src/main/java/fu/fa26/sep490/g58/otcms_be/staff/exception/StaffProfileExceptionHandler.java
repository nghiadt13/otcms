package fu.fa26.sep490.g58.otcms_be.staff.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class StaffProfileExceptionHandler {
    @ExceptionHandler(StaffProfileException.class)
    public ResponseEntity<ErrorResponse> handle(StaffProfileException ex) {
        HttpStatus status = switch (ex.getMessage()) {
            case "STAFF_PROFILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "STAFF_ACCOUNT_UNAVAILABLE", "STAFF_PROFILE_CONFLICT", "DOCTOR_HAS_FUTURE_APPOINTMENTS",
                    "STAFF_SHIFT_CONFLICT", "STAFF_SHIFT_NOT_EDITABLE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(ErrorResponse.of(msgCodeFor(ex.getMessage()),
                "Staff profile request could not be completed", UUID.randomUUID().toString()));
    }

    /** Maps the technical staff code to its R3 MSG code; the technical code only drives the HTTP status. */
    private static String msgCodeFor(String technical) {
        return switch (technical) {
            case "STAFF_PROFILE_NOT_FOUND", "STAFF_ACCOUNT_UNAVAILABLE", "STAFF_PROFILE_CONFLICT" -> "MSG-23";
            case "DOCTOR_HAS_FUTURE_APPOINTMENTS" -> "MSG-36";
            case "STAFF_SHIFT_CONFLICT" -> "MSG-35";
            case "STAFF_SHIFT_NOT_EDITABLE" -> "MSG-33";
            case "STAFF_SHIFT_REQUEST_INVALID" -> "MSG-34";
            case "VALIDATION_FAILED" -> "MSG-22";
            default -> "MSG-04";
        };
    }
}
