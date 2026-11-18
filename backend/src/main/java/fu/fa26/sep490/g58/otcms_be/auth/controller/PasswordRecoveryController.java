package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.exception.PasswordRecoveryException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ChangePasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ForgotPasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.PatientOtpResetRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.service.PasswordRecoveryService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;

    public PasswordRecoveryController(PasswordRecoveryService recovery) {
        this.recovery = recovery;
    }

    @PostMapping("/patient/forgot-password")
    public ResponseEntity<String> patientForgot(@Valid @RequestBody ForgotPasswordRequest request) {
        String message = recovery.forgot(request.email(), AccountType.PATIENT);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(message);
    }

    @PostMapping("/patient/reset-password")
    public ResponseEntity<Void> patientReset(@Valid @RequestBody PatientOtpResetRequest request) {
        recovery.resetPatientOtp(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> change(@AuthenticationPrincipal AuthenticatedAccount principal,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        if (principal == null) {
            throw new PasswordRecoveryException("AUTHENTICATION_FAILED");
        }
        recovery.change(principal.accountId(), request);
        return ResponseEntity.noContent().build();
    }
}
