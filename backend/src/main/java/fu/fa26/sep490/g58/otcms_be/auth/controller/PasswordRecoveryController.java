package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.exception.PasswordRecoveryException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.StaffNetworkAccessException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ChangePasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ForgotPasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.ResetPasswordRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.security.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.auth.service.PasswordRecoveryService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import jakarta.servlet.http.HttpServletRequest;
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
    private final StaffNetworkPolicy network;

    public PasswordRecoveryController(PasswordRecoveryService recovery, StaffNetworkPolicy network) {
        this.recovery = recovery;
        this.network = network;
    }

    @PostMapping("/patient/forgot-password")
    public ResponseEntity<String> patientForgot(@Valid @RequestBody ForgotPasswordRequest request) {
        String message = recovery.forgot(request.email(), AccountType.PATIENT);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(message);
    }

    @PostMapping("/staff/forgot-password")
    public ResponseEntity<String> staffForgot(@Valid @RequestBody ForgotPasswordRequest request,
                                               HttpServletRequest httpRequest) {
        requireStaffNetwork(httpRequest);
        String message = recovery.forgot(request.email(), AccountType.RECEPTIONIST);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(message);
    }

    @PostMapping("/patient/reset-password")
    public ResponseEntity<Void> patientReset(@Valid @RequestBody ResetPasswordRequest request) {
        recovery.reset(request, AccountType.PATIENT);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/staff/reset-password")
    public ResponseEntity<Void> staffReset(@Valid @RequestBody ResetPasswordRequest request,
                                           HttpServletRequest httpRequest) {
        requireStaffNetwork(httpRequest);
        recovery.reset(request, AccountType.RECEPTIONIST);
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

    private void requireStaffNetwork(HttpServletRequest request) {
        String address = network.clientAddress(request.getRemoteAddr(), request.getHeader("Forwarded"),
                request.getHeader("X-Forwarded-For"));
        if (!network.allows(address)) {
            throw new StaffNetworkAccessException();
        }
    }
}
