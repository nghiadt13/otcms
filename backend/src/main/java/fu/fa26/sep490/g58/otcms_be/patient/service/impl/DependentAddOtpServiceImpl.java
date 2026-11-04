package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.patient.exception.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.Relationship;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.entity.DependentAddRequestJpaEntity;
import fu.fa26.sep490.g58.otcms_be.patient.repository.DependentAddRequestRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.DependentAddOtpService;
import fu.fa26.sep490.g58.otcms_be.patient.service.DependentProfileService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UC13 email-OTP gate in front of {@link DependentProfileService#add}. The code goes to the
 * requester's own account email; nothing is created until it is verified. No caller transaction
 * may stay open around SMTP, so public methods run with Propagation.NEVER and use short
 * transactions of their own.
 */
@Service
public class DependentAddOtpServiceImpl implements DependentAddOtpService {
    private final PatientIdentityDirectory patients;
    private final MyAccountQueryService accounts;
    private final DependentAddRequestRepository requests;
    private final CoordinationGuardService guard;
    private final EmailOtpService otp;
    private final EmailDeliveryService email;
    private final AuthProperties auth;
    private final Clock clock;
    private final DependentProfileService dependents;
    private final TransactionTemplate transactions;

    public DependentAddOtpServiceImpl(PatientIdentityDirectory patients, MyAccountQueryService accounts,
            DependentAddRequestRepository requests, CoordinationGuardService guard, EmailOtpService otp,
            EmailDeliveryService email, AuthProperties auth, Clock clock, DependentProfileService dependents,
            PlatformTransactionManager transactionManager) {
        this.patients = patients; this.accounts = accounts; this.requests = requests; this.guard = guard;
        this.otp = otp; this.email = email; this.auth = auth; this.clock = clock; this.dependents = dependents;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public AddOtpResult request(UUID actor, AddDependentRequest request) {
        requirePatient(actor);
        if (request == null || request.relationship() == null) throw new CareRelationshipException("VALIDATION_FAILED");
        // Same normalization and validation the add flow applies, so bad input fails before any email.
        var normalized = AdministrativeProfileFields.record(UUID.randomUUID(), null, request.fullName(), null, null,
                request.dateOfBirth(), request.gender(), null, request.nationalId(), request.identityDocumentType(),
                0, false, true);
        return issue(actor, new AddDependentRequest(normalized.fullName(), normalized.dateOfBirth(),
                normalized.gender(), normalized.nationalId(), normalized.identityDocumentType(), request.relationship()));
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public AddOtpResult resend(UUID actor, UUID requestId) {
        requirePatient(actor);
        var stored = transactions.execute(status -> {
            var row = requests.findById(requestId)
                    .filter(r -> r.getRequesterAccountId().equals(actor)).orElse(null);
            if (row == null) throw new CareRelationshipException("CARE_LINK_NOT_FOUND");
            if (row.getConsumedAt() != null) throw new CareRelationshipException("CARE_LINK_NOT_AVAILABLE");
            return toRequest(row);
        });
        return issue(actor, stored);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public LinkResult verify(UUID actor, UUID requestId, String code) {
        requirePatient(actor);
        // The attempt counter must commit even when the code is wrong, so the outcome is returned
        // (committing) and the failure is thrown only after the transaction has closed.
        AddDependentRequest confirmed = transactions.execute(status -> {
            guard.acquire();
            var row = requests.findByIdForUpdate(requestId)
                    .filter(r -> r.getRequesterAccountId().equals(actor)).orElse(null);
            if (row == null) throw new CareRelationshipException("CARE_LINK_NOT_FOUND");
            Instant now = clock.instant();
            if (row.getConsumedAt() != null || row.getSupersededAt() != null || !row.getExpiresAt().isAfter(now)
                    || row.getFailedAttempts() >= EmailOtpService.MAX_FAILED_ATTEMPTS) return null;
            if (code == null || !code.matches("[0-9]{6}") || !otp.matches(code, row.getOtpHash())) {
                row.setFailedAttempts(row.getFailedAttempts() + 1);
                if (row.getFailedAttempts() >= EmailOtpService.MAX_FAILED_ATTEMPTS) row.setSupersededAt(now);
                requests.saveAndFlush(row);
                return null;
            }
            row.setConsumedAt(now);
            requests.saveAndFlush(row);
            return toRequest(row);
        });
        if (confirmed == null) throw new CareOtpInvalidException();
        return dependents.add(actor, confirmed);
    }

    private AddOtpResult issue(UUID actor, AddDependentRequest request) {
        String recipient = accounts.find(actor).map(MyAccountQueryService.MyAccountView::email)
                .filter(value -> value != null && !value.isBlank() && value.indexOf('@') > 0)
                .orElseThrow(() -> new CareRelationshipException("EMAIL_DELIVERY_UNAVAILABLE"));
        String code = otp.generate();
        UUID id = UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            guard.acquire();
            Instant now = clock.instant();
            Instant since = now.minusSeconds(auth.resend().windowSeconds());
            if (requests.countByRequesterAccountIdAndCreatedAtAfter(actor, since) >= auth.resend().requests())
                throw new CareRelationshipException("RATE_LIMIT_EXCEEDED");
            requests.supersedeOpenRequests(actor, now);
            var row = new DependentAddRequestJpaEntity();
            row.setId(id); row.setRequesterAccountId(actor);
            row.setFullName(request.fullName()); row.setDateOfBirth(request.dateOfBirth());
            row.setGender(request.gender()); row.setNationalId(request.nationalId());
            row.setIdentityDocumentType(request.identityDocumentType());
            row.setRelationship(request.relationship().name());
            // A hash of a non-code matches no six-digit input; the real hash is set only after SMTP succeeds.
            row.setOtpHash(otp.hash("DELIVERY_PENDING"));
            row.setExpiresAt(otp.expiresAt(now)); row.setCreatedAt(now);
            requests.saveAndFlush(row);
        });
        try { email.sendVerificationCode(recipient, code, actor); }
        catch (RuntimeException failure) {
            transactions.executeWithoutResult(status -> requests.findByIdForUpdate(id).ifPresent(row -> {
                row.setSupersededAt(clock.instant());
                requests.saveAndFlush(row);
            }));
            throw new CareRelationshipException("EMAIL_DELIVERY_UNAVAILABLE");
        }
        transactions.executeWithoutResult(status -> {
            guard.acquire();
            if (requests.activateIfCurrent(id, otp.hash(code), clock.instant(), EmailOtpService.MAX_FAILED_ATTEMPTS) != 1)
                throw new CareRelationshipException("CARE_LINK_NOT_AVAILABLE");
        });
        return new AddOtpResult(id, DependentProfileServiceImpl.maskEmail(recipient));
    }

    private void requirePatient(UUID actor) {
        if (actor == null || !patients.isActivePatient(actor)) throw new CareRelationshipException("ACCESS_DENIED");
    }

    private static AddDependentRequest toRequest(DependentAddRequestJpaEntity row) {
        return new AddDependentRequest(row.getFullName(), row.getDateOfBirth(), row.getGender(), row.getNationalId(),
                row.getIdentityDocumentType(), Relationship.valueOf(row.getRelationship()));
    }
}
