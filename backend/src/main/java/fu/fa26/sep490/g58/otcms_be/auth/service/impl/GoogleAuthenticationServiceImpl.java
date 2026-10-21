package fu.fa26.sep490.g58.otcms_be.auth.service.impl;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.GoogleAuthenticationService;

import fu.fa26.sep490.g58.otcms_be.auth.exception.GoogleAuthenticationException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.ConsentEvidenceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.ExternalIdentityJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.ConsentActorType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.IdentityProvider;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.PhoneOwnerType;
import fu.fa26.sep490.g58.otcms_be.auth.service.GoogleOidcService;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.ExternalIdentityRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.ConsentEvidenceRepository;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.GoogleExchangeRequest;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class GoogleAuthenticationServiceImpl implements GoogleAuthenticationService {
    private final GoogleOidcService google;
    private final AccountRepository accounts;
    private final ExternalIdentityRepository identities;
    private final AuthSessionService sessions;
    private final PatientProfileRepository profiles;
    private final ConsentEvidenceRepository consents;
    private final AuthProperties authProperties;

    public GoogleAuthenticationServiceImpl(GoogleOidcService google, AccountRepository accounts,
                                       ExternalIdentityRepository identities, AuthSessionService sessions,
                                       PatientProfileRepository profiles, ConsentEvidenceRepository consents, AuthProperties authProperties) {
        this.google = google; this.accounts = accounts; this.identities = identities; this.sessions = sessions;
        this.profiles = profiles; this.consents = consents; this.authProperties = authProperties;
    }

    @Transactional
    public AuthSessionService.IssuedSession exchange(String code, String verifier, String redirectUri,
                                                     String clientIp, String userAgent) {
        return exchange(new GoogleExchangeRequest(code, verifier, redirectUri), clientIp, userAgent);
    }

    @Transactional
    public AuthSessionService.IssuedSession exchange(GoogleExchangeRequest request, String clientIp, String userAgent) {
        var identity = google.exchange(request.authorizationCode(), request.codeVerifier(), request.redirectUri());
        if (!identity.emailVerified() || identity.subject() == null || identity.subject().isBlank())
            throw new GoogleAuthenticationException("AUTHENTICATION_FAILED");
        var linked = identities.findByProviderAndProviderSubject(IdentityProvider.GOOGLE, identity.subject());
        AccountJpaEntity account;
        if (linked.isPresent()) {
            account = linked.get().getAccount();
            if (!identity.normalizedEmail().equals(account.getNormalizedEmail()) || account.getAccountType() != AccountType.PATIENT)
                throw new GoogleAuthenticationException("IDENTITY_LINK_CONFLICT");
        } else {
            account = accounts.findByNormalizedEmail(identity.normalizedEmail()).orElseGet(() -> onboard(request, identity));
            if (account.getAccountType() != AccountType.PATIENT || account.getStatus() == AccountStatus.DISABLED
                    || account.getStatus() == AccountStatus.ARCHIVED)
                throw new GoogleAuthenticationException("IDENTITY_LINK_CONFLICT");
            ExternalIdentityJpaEntity link = new ExternalIdentityJpaEntity();
            link.setAccount(account); link.setProvider(IdentityProvider.GOOGLE);
            link.setProviderSubject(identity.subject()); link.setNormalizedEmailAtLink(identity.normalizedEmail());
            link.setLinkedAt(Instant.now()); identities.saveAndFlush(link);
        }
        if (account.getAccountType() != AccountType.PATIENT || account.getStatus() != AccountStatus.ACTIVE)
            throw new GoogleAuthenticationException("AUTHENTICATION_FAILED");
        return sessions.create(account.getId(), clientIp, userAgent);
    }

    private AccountJpaEntity onboard(GoogleExchangeRequest request, GoogleOidcService.VerifiedGoogleIdentity identity) {
        if (!request.hasOnboardingData() || request.fullName() == null || request.phone() == null || request.dateOfBirth() == null
                || request.gender() == null || !Boolean.TRUE.equals(request.patientConsent()))
            throw new GoogleAuthenticationException("GOOGLE_ONBOARDING_REQUIRED");
        if (request.fullName().trim().length() < 2 || request.fullName().trim().length() > 100
                || request.termsVersion() == null || request.privacyVersion() == null
                || !authProperties.termsVersion().equals(request.termsVersion())
                || !authProperties.privacyVersion().equals(request.privacyVersion()))
            throw new GoogleAuthenticationException("GOOGLE_ONBOARDING_REQUIRED");
        var account = new AccountJpaEntity(); account.setNormalizedEmail(identity.normalizedEmail()); account.setAccountType(AccountType.PATIENT);
        account.setStatus(AccountStatus.ACTIVE); account.setEmailVerifiedAt(Instant.now()); account.setCreatedAt(Instant.now()); account.setUpdatedAt(Instant.now());
        accounts.saveAndFlush(account);
        var profile = new PatientProfileJpaEntity(); profile.setAccount(account); profile.setAccountId(account.getId()); profile.setFullName(request.fullName().trim());
        profile.setPhoneCanonical(request.phone()); profile.setPhoneOwnerType(PhoneOwnerType.PATIENT); profile.setDateOfBirth(request.dateOfBirth()); profile.setGender(request.gender()); profiles.save(profile);
        var consent = new ConsentEvidenceJpaEntity(); consent.setAccount(account); consent.setActorType(ConsentActorType.PATIENT); consent.setTermsVersion(request.termsVersion()); consent.setPrivacyVersion(request.privacyVersion()); consent.setAcceptedAt(Instant.now()); consents.save(consent);
        return account;
    }
}
