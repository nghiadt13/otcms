package fu.fa26.sep490.g58.otcms_be.clinical.exception;

import fu.fa26.sep490.g58.otcms_be.clinical.controller.AfterVisitAdviceController;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.ClinicalExaminationController;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.ClinicalImagingRequestController;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.DoctorActiveVisitController;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.ClinicalMedicalDocumentController;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.ClinicalPrescriptionController;
import fu.fa26.sep490.g58.otcms_be.clinical.controller.ClinicalPastMedicalRecordsController;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {
    ClinicalExaminationController.class,
    AfterVisitAdviceController.class,
    ClinicalImagingRequestController.class,
    DoctorActiveVisitController.class,
    ClinicalPrescriptionController.class,
    ClinicalMedicalDocumentController.class,
    ClinicalPastMedicalRecordsController.class,
    fu.fa26.sep490.g58.otcms_be.clinical.controller.PatientPrescriptionController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClinicalApiExceptionHandler {
    @ExceptionHandler(fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException.class)
    public ResponseEntity<ErrorResponse> registration(
            fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException error, HttpServletRequest request) {
        return switch (error.outcome()) {
            case UNAUTHORIZED_RECORD -> problem(403, "MSG-40", List.of(), request);
            case RECORD_NOT_FOUND -> problem(404, "MSG-41", List.of(), request);
            case VISIT_NOT_ELIGIBLE, STATE_CONFLICT, VERSION_CONFLICT -> problem(409, "MSG-42", List.of(), request);
            case SERVICE_NOT_ELIGIBLE, EVIDENCE_MISMATCH -> problem(400, "MSG-39", List.of(), request);
            default -> problem(503, "DEPENDENCY_UNAVAILABLE", List.of(), request);
        };
    }

    @ExceptionHandler(ClinicalOperationException.class)
    public ResponseEntity<ErrorResponse> business(ClinicalOperationException error, HttpServletRequest request) {
        int status = switch (error.code()) {
            case "MSG-40", "MSG-14" -> 403;
            case "MSG-41" -> 404;
            case "MSG-42", "IDEMPOTENCY_CONFLICT" -> 409;
            case "DEPENDENCY_UNAVAILABLE" -> 503;
            case "MSG-39", "MSG-08", "MSG-47" -> 400;
            default -> 500;
        };
        return problem(status, error.code(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        return problem(400, "VALIDATION_FAILED", error.getBindingResult().getFieldErrors().stream()
                .map(field -> new ErrorResponse.FieldError(field.getField(), "MSG-39")).distinct().toList(), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class, org.springframework.web.HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> transport(Exception error, HttpServletRequest request) {
        return problem(400, "VALIDATION_FAILED", List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(Exception error, HttpServletRequest request) {
        return problem(403, "MSG-40", List.of(), request);
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<ErrorResponse> multipart(Exception error, HttpServletRequest request) {
        return problem(400, "MSG-47", List.of(), request);
    }
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> missingPart(Exception error, HttpServletRequest request) {
        return problem(400, "VALIDATION_FAILED", List.of(), request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> optimistic(Exception error, HttpServletRequest request) {
        return problem(409, "MSG-42", List.of(), request);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> dependency(Exception error, HttpServletRequest request) {
        return problem(503, "DEPENDENCY_UNAVAILABLE", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception error, HttpServletRequest request) {
        return problem(500, "MSG-04", List.of(), request);
    }

    private static ResponseEntity<ErrorResponse> problem(int status, String code,
            List<ErrorResponse.FieldError> fields, HttpServletRequest request) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(ErrorResponse.withFieldErrors(code, "Clinical request could not be completed",
                        CorrelationIdFilter.current(request), fields));
    }
}
