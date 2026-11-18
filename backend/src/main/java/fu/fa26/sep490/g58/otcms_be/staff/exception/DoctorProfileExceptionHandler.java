package fu.fa26.sep490.g58.otcms_be.staff.exception;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DoctorProfileExceptionHandler {
    @ExceptionHandler(DoctorProfileException.class)
    public ResponseEntity<ErrorResponse> handle(DoctorProfileException exception) {
        String code = exception.getMessage();
        HttpStatus status = "DOCTOR_ACCOUNT_UNAVAILABLE".equals(code) || "DOCTOR_PROFILE_NOT_FOUND".equals(code)
                || "DOCTOR_SHIFT_NOT_FOUND".equals(code) ? HttpStatus.NOT_FOUND
                : "DOCTOR_ACCOUNT_ALREADY_PROFILED".equals(code) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        if ("ACCESS_DENIED".equals(code)) status = HttpStatus.FORBIDDEN;
        else if ("DEPENDENCY_UNAVAILABLE".equals(code)) status = HttpStatus.SERVICE_UNAVAILABLE;
        else if (code.endsWith("_BLOCKED") || "DOCTOR_SHIFT_OVERLAP".equals(code)) status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(ErrorResponse.of(msgCodeFor(code), "Doctor profile request cannot be completed",
                UUID.randomUUID().toString()));
    }

    /** Maps the technical doctor-resource code to its R3 MSG code; the technical code only drives the HTTP status. */
    private static String msgCodeFor(String technical) {
        if (technical.endsWith("_BLOCKED")) return "MSG-36";
        return switch (technical) {
            case "DOCTOR_ACCOUNT_UNAVAILABLE", "DOCTOR_PROFILE_NOT_FOUND", "DOCTOR_ACCOUNT_ALREADY_PROFILED",
                    "ACCESS_DENIED" -> "MSG-23";
            case "DOCTOR_PROFILE_INVALID" -> "MSG-22";
            case "DOCTOR_SCHEDULE_RANGE_INVALID", "DOCTOR_RESOURCE_REQUEST_INVALID" -> "MSG-34";
            case "DOCTOR_SHIFT_NOT_FOUND", "DOCTOR_RESOURCE_IN_PAST" -> "MSG-33";
            case "DOCTOR_SHIFT_OVERLAP" -> "MSG-35";
            default -> "MSG-04"; // includes DEPENDENCY_UNAVAILABLE: no dedicated R3 code
        };
    }
}
