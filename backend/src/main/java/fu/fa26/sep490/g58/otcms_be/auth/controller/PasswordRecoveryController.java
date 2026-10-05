package fu.fa26.sep490.g58.otcms_be.auth.controller;

import fu.fa26.sep490.g58.otcms_be.auth.dto.*;
import fu.fa26.sep490.g58.otcms_be.auth.entity.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.security.JwtAuthenticationFilter.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.auth.service.PasswordRecoveryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PasswordRecoveryException;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery; private final StaffNetworkPolicy network;
    public PasswordRecoveryController(PasswordRecoveryService recovery, StaffNetworkPolicy network){this.recovery=recovery;this.network=network;}
    @PostMapping("/patient/forgot-password") public ResponseEntity<String> patientForgot(@Valid @RequestBody ForgotPasswordRequest r){return ResponseEntity.status(HttpStatus.ACCEPTED).body(recovery.forgot(r.email(),AccountType.PATIENT));}
    @PostMapping("/staff/forgot-password") public ResponseEntity<String> staffForgot(@Valid @RequestBody ForgotPasswordRequest r, jakarta.servlet.http.HttpServletRequest h){if(!network.allows(network.clientAddress(h.getRemoteAddr(),h.getHeader("Forwarded"),h.getHeader("X-Forwarded-For")))) throw new StaffNetworkAccessException(); return ResponseEntity.status(HttpStatus.ACCEPTED).body(recovery.forgot(r.email(),AccountType.STAFF));}
    @PostMapping("/patient/reset-password") public ResponseEntity<Void> patientReset(@Valid @RequestBody ResetPasswordRequest r){recovery.reset(r,AccountType.PATIENT);return ResponseEntity.noContent().build();}
    @PostMapping("/staff/reset-password") public ResponseEntity<Void> staffReset(@Valid @RequestBody ResetPasswordRequest r, jakarta.servlet.http.HttpServletRequest h){if(!network.allows(network.clientAddress(h.getRemoteAddr(),h.getHeader("Forwarded"),h.getHeader("X-Forwarded-For")))) throw new StaffNetworkAccessException(); recovery.reset(r,AccountType.STAFF);return ResponseEntity.noContent().build();}
    @PostMapping("/change-password") public ResponseEntity<Void> change(@AuthenticationPrincipal AuthenticatedAccount p,@Valid @RequestBody ChangePasswordRequest r){if(p==null) throw new PasswordRecoveryException("AUTHENTICATION_FAILED");recovery.change(p.accountId(),r);return ResponseEntity.noContent().build();}
}
