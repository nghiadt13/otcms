package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.EmailRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.OtpRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest;
import fu.fa26.sep490.g58.otcms_be.auth.service.RegistrationService;
import fu.fa26.sep490.g58.otcms_be.auth.exception.RegistrationException;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth/patient")
public class PatientRegistrationController {
    private final RegistrationService service;
    public PatientRegistrationController(RegistrationService service) { this.service = service; }
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegistrationRequest request, HttpServletRequest httpRequest) {
        try { return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.register(request, httpRequest.getRemoteAddr())); }
        catch (RegistrationException ex) { return error(ex); }
    }
    @PostMapping("/verify-email")
    public ResponseEntity<?> verify(@Valid @RequestBody OtpRequest request) {
        try { service.verifyEmail(request.email(), request.otp()); return ResponseEntity.noContent().build(); }
        catch (RegistrationException ex) { return error(ex); }
    }
    @PostMapping("/resend-otp")
    public ResponseEntity<?> resend(@Valid @RequestBody EmailRequest request) {
        try { service.resendOtp(request.email()); return ResponseEntity.accepted().build(); }
        catch (RegistrationException ex) { return error(ex); }
    }
    private ResponseEntity<ErrorResponse> error(RegistrationException ex) {
        HttpStatus status = "RATE_LIMIT_EXCEEDED".equals(ex.getMessage()) ? HttpStatus.TOO_MANY_REQUESTS
                : "EMAIL_DELIVERY_UNAVAILABLE".equals(ex.getMessage())
                || "RATE_LIMIT_UNAVAILABLE".equals(ex.getMessage()) ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(ErrorResponse.of(ex.getMessage(), "Authentication request could not be completed", null));
    }
}
