package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.patient.exception.*;
import fu.fa26.sep490.g58.otcms_be.patient.mapper.CareRelationshipMapper;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileDetailResponse;
import fu.fa26.sep490.g58.otcms_be.patient.model.entity.CareLinkOtpChallengeJpaEntity;
import fu.fa26.sep490.g58.otcms_be.patient.repository.*;
import fu.fa26.sep490.g58.otcms_be.patient.service.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(readOnly = true)
public class DependentProfileServiceImpl implements DependentProfileService, CounterCareLinkService, RevokeCareRelationshipService {
    private final PatientProfileDirectory profiles;
    private final PatientIdentityDirectory patients;
    private final ReceptionistAccountAccessService receptionists;
    private final MyAccountQueryService accounts;
    private final CareRelationshipRepository links;
    private final CareLinkOtpChallengeRepository challenges;
    private final CoordinationGuardService guard;
    private final EmailOtpService otp;
    private final EmailDeliveryService email;
    private final AuthProperties auth;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public DependentProfileServiceImpl(PatientProfileDirectory profiles, PatientIdentityDirectory patients,
            ReceptionistAccountAccessService receptionists, MyAccountQueryService accounts,
            CareRelationshipRepository links, CareLinkOtpChallengeRepository challenges, CoordinationGuardService guard,
            EmailOtpService otp, EmailDeliveryService email, AuthProperties auth, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.profiles = profiles; this.patients = patients; this.receptionists = receptionists;
        this.accounts = accounts; this.links = links; this.challenges = challenges; this.guard = guard;
        this.otp = otp; this.email = email; this.auth = auth; this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public LinkResult add(UUID actor, AddDependentRequest request) {
        return deliver(Objects.requireNonNull(transactions.execute(status -> prepareAdd(actor, request))));
    }

    private PreparedLink prepareAdd(UUID actor, AddDependentRequest request) {
        requirePatient(actor);
        if (request == null || request.relationship() == null) throw invalid();
        var input = AdministrativeProfileFields.record(UUID.randomUUID(), null, request.fullName(), null, null,
                request.dateOfBirth(), request.gender(), null, request.nationalId(), request.identityDocumentType(),
                0, false, true);
        guard.acquire();
        var candidates = profiles.matchDependent(input.fullName(), input.dateOfBirth(), input.nationalId());
        if (candidates.size() > 1) throw unavailable();
        Instant now = clock.instant();
        if (candidates.isEmpty()) {
            var created = profiles.create(input, null);
            var link = new CareRelationship(UUID.randomUUID(), actor, created.id(), null, State.CONFIRMED,
                    now, now, null, null, request.relationship(), VerificationMethod.CREATED_WITHOUT_OTP, null, actor, now);
            save(link);
            return new PreparedLink(linked(link, created), null);
        }
        var profile = candidates.getFirst();
        if (actor.equals(profile.accountId()) || profiles.findPatientProfile(actor).filter(profile.id()::equals).isPresent())
            throw unavailable();
        if (input.identityDocumentType() != null && profile.identityDocumentType() != null
                && !input.identityDocumentType().equals(profile.identityDocumentType())) throw unavailable();
        if (links.findFirstByRequesterAccountIdAndDependentPatientProfileIdAndStateIn(actor, profile.id(),
                List.of(State.REQUESTED, State.CONFIRMED)).isPresent()) throw unavailable();
        String recipient = recipientEmail(profile);
        boolean useOtp = recipient != null && profile.nationalId() != null && !profile.nationalId().isBlank()
                && "CCCD".equals(profile.identityDocumentType());
        var link = new CareRelationship(UUID.randomUUID(), actor, profile.id(), profile.accountId(), State.REQUESTED,
                now, null, null, null, request.relationship(), null, useOtp ? PendingReason.OTP : PendingReason.COUNTER,
                null, null);
        save(link);
        if (useOtp) return prepareOtp(link, profile, recipient, now);
        return new PreparedLink(new LinkResult("COUNTER_REQUIRED", link.id(), null, null), null);
    }

    @Override
    @Transactional(noRollbackFor = CareOtpInvalidException.class)
    public LinkResult verify(UUID actor, UUID linkId, String code) {
        requirePatient(actor);
        guard.acquire();
        var link = owned(actor, linkId);
        requirePending(link, PendingReason.OTP);
        var challenge = challenges.findCurrentForUpdate(linkId).orElseThrow(CareOtpInvalidException::new);
        Instant now = clock.instant();
        var profile = profile(link.dependentPatientProfileId());
        if (!Objects.equals(challenge.getRecipientAccountId(), profile.accountId())
                || !Objects.equals(link.confirmingAccountId(), profile.accountId())
                || challenge.getConsumedAt() != null || challenge.getSupersededAt() != null
                || !challenge.getExpiresAt().isAfter(now) || challenge.getFailedAttempts() >= EmailOtpService.MAX_FAILED_ATTEMPTS)
            throw new CareOtpInvalidException();
        if (code == null || !code.matches("[0-9]{6}") || !otp.matches(code, challenge.getOtpHash())) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            if (challenge.getFailedAttempts() >= EmailOtpService.MAX_FAILED_ATTEMPTS) challenge.setSupersededAt(now);
            challenges.saveAndFlush(challenge);
            throw new CareOtpInvalidException();
        }
        challenge.setConsumedAt(now);
        challenges.saveAndFlush(challenge);
        var confirmed = link.verified(VerificationMethod.EMAIL_OTP, actor, now);
        save(confirmed);
        return linked(confirmed, profile);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public LinkResult resend(UUID actor, UUID linkId) {
        return deliver(Objects.requireNonNull(transactions.execute(status -> prepareResend(actor, linkId))));
    }

    private PreparedLink prepareResend(UUID actor, UUID linkId) {
        requirePatient(actor);
        guard.acquire();
        var link = owned(actor, linkId);
        requirePending(link, PendingReason.OTP);
        var profile = profile(link.dependentPatientProfileId());
        String recipient = recipientEmail(profile);
        if (recipient == null || !Objects.equals(profile.accountId(), link.confirmingAccountId())) throw unavailable();
        return prepareOtp(link, profile, recipient, clock.instant());
    }

    private PreparedLink prepareOtp(CareRelationship link, ProfileRecord profile, String recipient, Instant now) {
        Instant since = now.minusSeconds(auth.resend().windowSeconds());
        if (challenges.countByRecipientAccountIdAndCreatedAtAfter(profile.accountId(), since) >= auth.resend().requests()
                || challenges.countByLinkIdAndCreatedAtAfter(link.id(), since) >= auth.resend().requests())
            throw new CareRelationshipException("RATE_LIMIT_EXCEEDED");
        challenges.findCurrentForUpdate(link.id()).ifPresent(previous -> {
            previous.setSupersededAt(now);
            challenges.saveAndFlush(previous);
        });
        String code = otp.generate();
        var challenge = new CareLinkOtpChallengeJpaEntity();
        challenge.setId(UUID.randomUUID()); challenge.setLinkId(link.id());
        challenge.setRecipientAccountId(profile.accountId());
        // A hash of a non-code cannot match any valid six-digit request. Activation happens
        // only after SMTP succeeds; even failed compensation cannot enable an unsent code.
        challenge.setOtpHash(otp.hash("DELIVERY_PENDING"));
        challenge.setExpiresAt(otp.expiresAt(now)); challenge.setCreatedAt(now);
        challenges.saveAndFlush(challenge);
        return new PreparedLink(new LinkResult("OTP_SENT", link.id(), maskEmail(recipient), null),
                new OtpDelivery(challenge.getId(), link.id(), profile.accountId(), recipient, code, otp.hash(code)));
    }

    /** No caller transaction may be suspended with locks still held around SMTP (Propagation.NEVER). */
    private LinkResult deliver(PreparedLink prepared) {
        OtpDelivery delivery = prepared.delivery();
        if (delivery == null) return prepared.result();
        try { email.sendVerificationCode(delivery.recipient(), delivery.code(), delivery.recipientAccountId()); }
        catch (RuntimeException failure) {
            transactions.executeWithoutResult(status -> {
                guard.acquire();
                challenges.findByIdForUpdate(delivery.challengeId()).ifPresent(challenge -> {
                    if (challenge.getConsumedAt() == null && challenge.getSupersededAt() == null) {
                        challenge.setSupersededAt(clock.instant());
                        challenges.saveAndFlush(challenge);
                    }
                });
            });
            throw new CareRelationshipException("EMAIL_DELIVERY_UNAVAILABLE");
        }
        transactions.executeWithoutResult(status -> {
            guard.acquire();
            var link = requiredLink(delivery.linkId());
            requirePending(link, PendingReason.OTP);
            // Bulk mutation uses DB eligibility and changes only the hash. An OSIV-managed
            // prepare-phase entity must never overwrite attempts committed during SMTP.
            if (challenges.activateIfCurrent(delivery.challengeId(), delivery.linkId(), delivery.hash(),
                    clock.instant(), EmailOtpService.MAX_FAILED_ATTEMPTS) != 1) throw unavailable();
        });
        return prepared.result();
    }

    private record PreparedLink(LinkResult result, OtpDelivery delivery) { }
    private record OtpDelivery(UUID challengeId, UUID linkId, UUID recipientAccountId,
            String recipient, String code, String hash) {
        @Override public String toString() { return "OtpDelivery[redacted]"; }
    }

    @Override
    public Items<ProfileItem> list(UUID actor) {
        UUID ownId = requirePatient(actor);
        var own = profile(ownId);
        List<ProfileItem> items = new ArrayList<>();
        items.add(item(own, "SELF", "CONFIRMED", null));
        for (var entity : links.findAllByRequesterAccountIdOrderByRequestedAtAscIdAsc(actor)) {
            var link = CareRelationshipMapper.toModel(entity);
            // Legacy relationships have no truthful R3 relationship/status projection.
            if (link.state() == State.REVOKED || link.relationship() == null) continue;
            String status = link.state() == State.CONFIRMED ? "CONFIRMED"
                    : link.pendingReason() == PendingReason.OTP ? "PENDING_OTP"
                    : link.pendingReason() == PendingReason.COUNTER ? "PENDING_COUNTER" : null;
            if (status != null) items.add(item(profile(link.dependentPatientProfileId()), link.relationship().name(), status, link.id()));
        }
        return new Items<>(List.copyOf(items));
    }

    @Override
    public PatientProfileDetailResponse detail(UUID actor, UUID profileId) {
        requirePatient(actor);
        confirmed(actor, profileId);
        return detail(profile(profileId));
    }

    @Override
    @Transactional
    public PatientProfileDetailResponse update(UUID actor, UUID profileId, UpdateDependentRequest request) {
        requirePatient(actor);
        guard.acquire();
        confirmed(actor, profileId);
        if (request == null) throw invalid();
        var current = profile(profileId);
        var input = AdministrativeProfileFields.record(profileId, current.accountId(), request.fullName(), request.phone(),
                current.phoneOwnerType(), request.dateOfBirth(), request.gender(), request.address(), request.nationalId(),
                request.identityDocumentType(), current.version(), false, current.accountId() == null && current.phoneOwnerType() == null);
        return detail(profiles.update(input, current.version()));
    }

    @Override
    @Transactional
    public void unlink(UUID actor, UUID profileId) {
        requirePatient(actor);
        guard.acquire();
        save(confirmed(actor, profileId).revoke(actor, clock.instant()));
    }

    @Override
    @Transactional
    public CareRelationship revoke(UUID actor, UUID linkId) {
        requirePatient(actor);
        guard.acquire();
        var link = owned(actor, linkId);
        if (link.state() != State.CONFIRMED) throw missing();
        var revoked = link.revoke(actor, clock.instant());
        save(revoked);
        return revoked;
    }

    @Override
    public Items<CounterItem> pending(UUID actor, String query) {
        requireReceptionist(actor);
        String term = AdministrativeProfileFields.normalizeName(query);
        if (term.length() > 100) throw invalid();
        String folded = term.toLowerCase(Locale.ROOT);
        List<CounterItem> result = new ArrayList<>();
        for (var entity : links.findAllByStateAndPendingReasonOrderByRequestedAtAscIdAsc(State.REQUESTED, PendingReason.COUNTER)) {
            var p = profile(entity.getDependentPatientProfileId());
            if (!term.isEmpty() && !AdministrativeProfileFields.normalizeName(p.fullName()).toLowerCase(Locale.ROOT).contains(folded)
                    && (p.nationalId() == null || !p.nationalId().contains(term))) continue;
            result.add(new CounterItem(entity.getId(), p.id(), p.fullName(), p.dateOfBirth(), p.gender(),
                    p.nationalId(), p.identityDocumentType(), entity.getRelationship(), entity.getRequestedAt()));
        }
        return new Items<>(List.copyOf(result));
    }

    @Override
    @Transactional
    public LinkResult confirmCounter(UUID actor, UUID linkId, Boolean identityChecked) {
        requireReceptionist(actor);
        if (!Boolean.TRUE.equals(identityChecked)) throw invalid();
        guard.acquire();
        var link = requiredLink(linkId);
        requirePending(link, PendingReason.COUNTER);
        var verified = link.verified(VerificationMethod.COUNTER_CHECK, actor, clock.instant());
        save(verified);
        return linked(verified, profile(link.dependentPatientProfileId()));
    }

    @Override
    @Transactional
    public void rejectCounter(UUID actor, UUID linkId) {
        requireReceptionist(actor);
        guard.acquire();
        var link = requiredLink(linkId);
        requirePending(link, PendingReason.COUNTER);
        save(link.revoke(actor, clock.instant()));
    }

    private UUID requirePatient(UUID actor) {
        if (actor == null || !patients.isActivePatient(actor)) throw new CareRelationshipException("ACCESS_DENIED");
        return profiles.findPatientProfile(actor).orElseThrow(() -> new CareRelationshipException("ACCESS_DENIED"));
    }
    private void requireReceptionist(UUID actor) {
        if (actor == null || !receptionists.isActiveReceptionistAccount(actor)) throw new CareRelationshipException("ACCESS_DENIED");
    }
    private CareRelationship owned(UUID actor, UUID id) {
        var link = requiredLink(id);
        if (!actor.equals(link.requesterAccountId()) || link.state() == State.REVOKED) throw missing();
        return link;
    }
    private CareRelationship requiredLink(UUID id) {
        if (id == null) throw missing();
        return links.findById(id).map(CareRelationshipMapper::toModel).orElseThrow(DependentProfileServiceImpl::missing);
    }
    private CareRelationship confirmed(UUID actor, UUID profileId) {
        if (profileId == null) throw missing();
        return links.findFirstByRequesterAccountIdAndDependentPatientProfileIdAndStateIn(actor, profileId, List.of(State.CONFIRMED))
                .map(CareRelationshipMapper::toModel).orElseThrow(DependentProfileServiceImpl::missing);
    }
    private void requirePending(CareRelationship link, PendingReason reason) {
        if (link.state() == State.REVOKED) throw missing();
        if (link.state() != State.REQUESTED || link.pendingReason() != reason) throw unavailable();
    }
    private ProfileRecord profile(UUID id) { return profiles.findProfile(id).orElseThrow(DependentProfileServiceImpl::missing); }
    private String recipientEmail(ProfileRecord profile) {
        if (profile.accountId() == null) return null;
        return accounts.find(profile.accountId()).map(MyAccountQueryService.MyAccountView::email)
                .filter(value -> value != null && !value.isBlank() && value.indexOf('@') > 0).orElse(null);
    }
    private void save(CareRelationship link) { links.saveAndFlush(CareRelationshipMapper.toEntity(link)); }
    private LinkResult linked(CareRelationship link, ProfileRecord profile) {
        return new LinkResult("LINKED", link.id(), null, detail(profile));
    }
    private static ProfileItem item(ProfileRecord p, String relationship, String status, UUID link) {
        return new ProfileItem(p.id(), p.fullName(), p.dateOfBirth(), p.gender(), relationship, status, link);
    }
    private static PatientProfileDetailResponse detail(ProfileRecord p) {
        return new PatientProfileDetailResponse(p.id(), p.fullName(), p.phoneCanonical(), p.phoneOwnerType(), p.dateOfBirth(),
                p.gender(), p.address(), p.nationalId(), p.identityDocumentType(), p.accountId() != null, p.version());
    }
    static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        int at = email.lastIndexOf('@');
        if (at < 1 || at == email.length() - 1) return "***";
        // Mask the entire local part, including one-character addresses.
        return "***@" + email.substring(at + 1);
    }
    private static CareRelationshipException invalid() { return new CareRelationshipException("VALIDATION_FAILED"); }
    private static CareRelationshipException missing() { return new CareRelationshipException("CARE_LINK_NOT_FOUND"); }
    private static CareRelationshipException unavailable() { return new CareRelationshipException("CARE_LINK_NOT_AVAILABLE"); }
}
