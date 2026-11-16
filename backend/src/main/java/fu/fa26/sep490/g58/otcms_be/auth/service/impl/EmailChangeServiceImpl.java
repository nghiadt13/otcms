package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.MyAccountOperationException;
import fu.fa26.sep490.g58.otcms_be.auth.exception.EmailChangeWriteException;
import static fu.fa26.sep490.g58.otcms_be.auth.exception.MyAccountOperationException.Reason.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailChangeServiceImpl implements EmailChangeService {
    private final AccountRepository accounts;
    private final VerificationCodeRepository challenges;
    private final EmailOtpService otp;
    private final EmailDeliveryService delivery;
    private final AuthProperties properties;
    private final AuthConcurrencyRepository coordination;
    private final EmailChangeAccountWriter emailWriter;

    public EmailChangeServiceImpl(AccountRepository accounts, VerificationCodeRepository challenges,
            EmailOtpService otp, EmailDeliveryService delivery, AuthProperties properties, AuthConcurrencyRepository coordination,
            EmailChangeAccountWriter emailWriter) {
        this.accounts = accounts; this.challenges = challenges; this.otp = otp;
        this.delivery = delivery; this.properties = properties; this.coordination = coordination;
        this.emailWriter = emailWriter;
    }

    @Override @Transactional
    public void request(AuthenticatedAccount actor, String rawEmail) {
        var account = patient(actor);
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || email.codePoints().anyMatch(Character::isISOControl))
            throw new MyAccountOperationException(INVALID_INPUT);
        Instant now = Instant.now();
        // Latest valid intent invalidates the old OTP even when no replacement can be issued.
        challenges.supersedeCurrent(account.getId(), VerificationPurpose.EMAIL_CHANGE, now);
        if (email.equals(account.getNormalizedEmail()) || accounts.existsByNormalizedEmail(email)
                || challenges.countByAccountIdAndPurposeAndCreatedAtAfter(account.getId(), VerificationPurpose.EMAIL_CHANGE,
                now.minusSeconds(properties.resend().windowSeconds())) >= properties.resend().requests()) return;
        String code = otp.generate();
        var challenge = new VerificationCodeJpaEntity();
        challenge.setAccountId(account.getId());
        challenge.setPurpose(VerificationPurpose.EMAIL_CHANGE);
        challenge.setTargetEmail(email);
        challenge.setCodeHash(otp.hash(code));
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(otp.expiresAt(now));
        challenges.saveAndFlush(challenge);
        try {
            delivery.sendVerificationCode(email, code, account.getId());
        } catch (RuntimeException ignored) {
            // Preserve generic 202 and supersession when delivery is unavailable; never log recipient/code.
            challenge.setSupersededAt(now);
        }
    }

    @Override @Transactional(noRollbackFor = MyAccountOperationException.class, rollbackFor = EmailChangeWriteException.class)
    public void verify(AuthenticatedAccount actor, String code) {
        var account = patient(actor);
        var challenge = challenges.findCurrentForUpdate(account.getId(), VerificationPurpose.EMAIL_CHANGE)
                .orElseThrow(() -> new MyAccountOperationException(OTP_INVALID));
        Instant now = Instant.now();
        if (!challenge.getExpiresAt().isAfter(now) || challenge.getFailedAttempts() >= EmailOtpService.MAX_FAILED_ATTEMPTS)
            throw new MyAccountOperationException(OTP_INVALID);
        if (code == null || !code.matches("[0-9]{6}") || !otp.matches(code, challenge.getCodeHash())) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            if (challenge.getFailedAttempts() >= EmailOtpService.MAX_FAILED_ATTEMPTS) challenge.setSupersededAt(now);
            throw new MyAccountOperationException(OTP_INVALID);
        }
        String email = challenge.getTargetEmail();
        // Same target coordination namespace as UC06. DB uniqueness also guards non-cooperating writers.
        coordination.lock("registration-email:" + email);
        if (accounts.existsByNormalizedEmail(email)) {
            challenge.setSupersededAt(now);
            throw new MyAccountOperationException(EMAIL_UNAVAILABLE);
        }
        emailWriter.updateVerifiedEmail(account, email, now);
        challenge.setConsumedAt(now);
        challenges.flush();
    }

    private AccountJpaEntity patient(AuthenticatedAccount actor) {
        if (actor == null || actor.accountRole() != AccountRole.PATIENT || actor.accountId() == null)
            throw new MyAccountOperationException(ACCESS_DENIED);
        var a = accounts.findByIdForUpdate(actor.accountId()).orElseThrow(() -> new MyAccountOperationException(ACCESS_DENIED));
        if (a.getAccountType() != AccountType.PATIENT || a.getStatus() != AccountStatus.ACTIVE || a.isMustChangePassword())
            throw new MyAccountOperationException(ACCESS_DENIED);
        return a;
    }
}
