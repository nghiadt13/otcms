package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.CredentialLoginException;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.CredentialLoginResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AuthenticationAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthConcurrencyRepository;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AuthenticationAuditRepository;
import fu.fa26.sep490.g58.otcms_be.auth.service.CredentialLoginService;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service
public class CredentialLoginServiceImpl implements CredentialLoginService {
    private final AccountRepository accounts;
    private final AuthenticationAuditRepository audits;
    private final PasswordEncoder encoder;
    private final AuthProperties properties;
    private final AuthConcurrencyRepository concurrency;
    private final Clock clock;

    @Autowired
    public CredentialLoginServiceImpl(AccountRepository accounts, AuthenticationAuditRepository audits,
                                      PasswordEncoder encoder, AuthProperties properties,
                                      AuthConcurrencyRepository concurrency) {
        this(accounts, audits, encoder, properties, concurrency, Clock.systemUTC());
    }

    public CredentialLoginServiceImpl(AccountRepository accounts, AuthenticationAuditRepository audits,
                                      PasswordEncoder encoder, AuthProperties properties,
                                      AuthConcurrencyRepository concurrency, Clock clock) {
        this.accounts = accounts;
        this.audits = audits;
        this.encoder = encoder;
        this.properties = properties;
        this.concurrency = concurrency;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = CredentialLoginException.class)
    public CredentialLoginResponse login(String email, String password, AccountType portal, String ip) {
        return loginMatching(email, password, type -> type == portal, portal.name(), ip);
    }

    @Override
    @Transactional(noRollbackFor = CredentialLoginException.class)
    public CredentialLoginResponse loginStaff(String identifier, String password, String ip) {
        // Existing accounts can still use email; current Clinic Staff accounts use username.
        if (identifier != null && !identifier.contains("@")) {
            return authenticate(identifier, password, AccountType::isStaff, "STAFF", ip,
                    () -> accounts.findByUsername(identifier).orElse(null));
        }
        return loginMatching(identifier, password, AccountType::isStaff, "STAFF", ip);
    }

    @Override
    @Transactional(noRollbackFor = CredentialLoginException.class)
    public CredentialLoginResponse loginAdministrator(String username, String password, String ip) {
        return authenticate(username, password, type -> type == AccountType.ADMIN || type == AccountType.OWNER, "STAFF", ip,
                () -> accounts.findByUsername(username).orElse(null));
    }

    private CredentialLoginResponse loginMatching(String email, String password,
                                                  Predicate<AccountType> portalMatches,
                                                  String portal, String ip) {
        String normalizedEmail = normalize(email);
        return authenticate(normalizedEmail, password, portalMatches, portal, ip,
                () -> accounts.findByNormalizedEmail(normalizedEmail).orElse(null));
    }

    private CredentialLoginResponse authenticate(String identifier, String password,
            Predicate<AccountType> portalMatches, String portal, String ip, Supplier<AccountJpaEntity> lookup) {
        Instant now = Instant.now(clock);
        enforceIpRate(ip);

        AccountJpaEntity account = lookup.get();
        boolean authenticated = account != null
                && portalMatches.test(account.getAccountType())
                && usable(account, now)
                && account.getPasswordHash() != null
                && encoder.matches(password, account.getPasswordHash());
        if (!authenticated) {
            if (account != null && portalMatches.test(account.getAccountType())) {
                fail(account, now);
            }
            audit(account, account == null ? identifier : null,
                    "LOGIN_" + portal, "AUTHENTICATION_FAILED");
            throw new CredentialLoginException("AUTHENTICATION_FAILED");
        }

        account.setFailedCredentialCount(0);
        account.setLockedUntil(null);
        account.setUpdatedAt(now);
        accounts.save(account);
        audit(account, null, "LOGIN_" + portal, "SUCCESS");
        return new CredentialLoginResponse(account.getId(), account.getAccountType().name());
    }

    private boolean usable(AccountJpaEntity account, Instant now) {
        if (account.getStatus() == AccountStatus.ACTIVE) {
            return true;
        }
        if (account.getStatus() == AccountStatus.TEMPORARILY_LOCKED
                && account.getLockedUntil() != null
                && !now.isBefore(account.getLockedUntil())) {
            account.setStatus(AccountStatus.ACTIVE);
            account.setFailedCredentialCount(0);
            account.setLockedUntil(null);
            return true;
        }
        return false;
    }

    private void fail(AccountJpaEntity account, Instant now) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return;
        }
        int failedAttempts = account.getFailedCredentialCount() + 1;
        account.setFailedCredentialCount(failedAttempts);
        if (failedAttempts >= properties.credentialFailure().requests()) {
            account.setStatus(AccountStatus.TEMPORARILY_LOCKED);
            account.setLockedUntil(now.plusSeconds(properties.credentialFailure().windowSeconds()));
        }
        account.setUpdatedAt(now);
        accounts.save(account);
    }

    private void audit(AccountJpaEntity account, String identifier, String method, String outcome) {
        AuthenticationAuditJpaEntity audit = new AuthenticationAuditJpaEntity();
        audit.setOccurredAt(Instant.now(clock));
        audit.setMethod(method);
        audit.setOutcome(outcome);
        audit.setAccount(account);
        audit.setIdentifierHmac(account == null ? hmac(identifier) : null);
        audit.setCorrelationId(UUID.randomUUID().toString());
        audit.setClientIp(null);
        audit.setDurationMs(0);
        audits.save(audit);
    }

    private void enforceIpRate(String ip) {
        String key = ip == null ? "unknown" : ip.trim();
        String identifierHash = hmac(key);
        concurrency.lock("login-ip:" + identifierHash);
        Instant since = Instant.now(clock).minusSeconds(properties.login().windowSeconds());
        if (audits.countByMethodAndOutcomeAndIdentifierHmacAndOccurredAtAfter(
                "LOGIN_IP", "ATTEMPT", identifierHash, since) >= properties.login().requests()) {
            throw new CredentialLoginException("RATE_LIMIT_EXCEEDED");
        }

        AuthenticationAuditJpaEntity audit = new AuthenticationAuditJpaEntity();
        audit.setOccurredAt(Instant.now(clock));
        audit.setMethod("LOGIN_IP");
        audit.setOutcome("ATTEMPT");
        audit.setIdentifierHmac(identifierHash);
        audit.setCorrelationId(UUID.randomUUID().toString());
        audit.setClientIp(null);
        audit.setDurationMs(0);
        audits.save(audit);
    }

    private String hmac(String value) {
        String secret = properties.rateLimitHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw new CredentialLoginException("RATE_LIMIT_UNAVAILABLE");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new CredentialLoginException("RATE_LIMIT_UNAVAILABLE");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
