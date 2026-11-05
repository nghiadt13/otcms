package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ViewAuthorization;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingStudyPort;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingViewerGrantJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingViewerGrantRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;

/** Issues a five-minute actor/purpose/study/version grant; the raw random token is returned only as a cookie. */
@Service
public class ImagingViewerGrantService {
    private static final long MAX_TTL_SECONDS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ImagingStudyRepository studies;
    private final ImagingViewerGrantRepository grants;
    private final ObjectProvider<ImagingStudyPort> ownerPorts;
    private final ImagingAuditPersistenceService audit;

    public ImagingViewerGrantService(ImagingStudyRepository studies, ImagingViewerGrantRepository grants,
            ObjectProvider<ImagingStudyPort> ownerPorts, ImagingAuditPersistenceService audit) {
        this.studies = studies; this.grants = grants; this.ownerPorts = ownerPorts; this.audit = audit;
    }

    @Transactional(noRollbackFor = ImagingOperationException.class)
    public IssuedGrant issue(UUID actorId, String studyRef, String purpose, String correlationId) {
        var study = studies.findById(studyRef).orElse(null);
        if (study == null) {
            audit.recordActor(actorId, "IMAGING_VIEWER_GRANT", "DENIED", null, studyRef,
                    null, null, null, null, correlationId,
                    java.util.Map.of("purpose", purpose, "reasonCode", "STUDY_NOT_FOUND"));
            throw new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD);
        }
        ViewAuthorization decision;
        try {
            ImagingStudyPort owner = ownerPorts.getIfAvailable();
            if (owner == null) throw new IllegalStateException("FEAT-003 owner contract is unavailable");
            decision = owner.authorizeStudyView(studyRef, actorId, purpose);
        } catch (RuntimeException unavailable) {
            audit.recordActor(actorId, "IMAGING_VIEWER_GRANT", "DEPENDENCY_UNAVAILABLE", null, studyRef,
                    study.getRequestId(), study.getAttemptId(), study.getVisitId(), study.getPatientId(),
                    correlationId, java.util.Map.of("purpose", purpose, "reasonCode", "OWNER_UNAVAILABLE"));
            throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
        }
        Instant now = Instant.now();
        if (decision == null || !decision.allowed() || decision.authorizedVersionRef() == null
                || decision.expiresAt() == null || !decision.expiresAt().isAfter(now)) {
            audit.recordActor(actorId, "IMAGING_VIEWER_GRANT", "DENIED", null, studyRef,
                    study.getRequestId(), study.getAttemptId(), study.getVisitId(), study.getPatientId(),
                    correlationId, java.util.Map.of("purpose", purpose));
            throw new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD);
        }
        Instant expiresAt = now.plusSeconds(Math.min(MAX_TTL_SECONDS,
                Math.max(1, decision.expiresAt().getEpochSecond() - now.getEpochSecond())));
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String tokenHash = sha256(token);
        String cookieName = cookieName(studyRef);
        audit.recordActor(actorId, "IMAGING_VIEWER_GRANT", "ISSUED", null, studyRef,
                study.getRequestId(), study.getAttemptId(), study.getVisitId(), study.getPatientId(),
                correlationId, java.util.Map.of("purpose", purpose, "expiresAt", expiresAt.toString()));
        grants.revokeActive(studyRef, actorId, now);
        grants.saveAndFlush(new ImagingViewerGrantJpaEntity(UUID.randomUUID(), tokenHash, actorId, purpose,
                studyRef, decision.authorizedVersionRef(), now, expiresAt, null));
        return new IssuedGrant(cookieName, token, expiresAt);
    }

    /** Revalidates the opaque cookie and owner decision for each gateway request or stream checkpoint. */
    public boolean isAuthorized(String studyRef, String token) {
        if (studyRef == null || token == null || token.length() < 32 || token.length() > 128) return false;
        try {
            Instant now = Instant.now();
            var grant = grants.findByTokenSha256(sha256(token));
            if (grant.isEmpty()) return false;
            var persisted = grant.get();
            if (!studyRef.equals(persisted.getStudyRef()) || persisted.getRevokedAt() != null
                    || !persisted.getExpiresAt().isAfter(now)) return false;
            ImagingStudyPort owner = ownerPorts.getIfAvailable();
            if (owner == null) return false;
            ViewAuthorization decision = owner.authorizeStudyView(studyRef, persisted.getActorAccountId(),
                    persisted.getPurpose());
            return decision != null && decision.allowed()
                    && persisted.getAuthorizedVersionRef().equals(decision.authorizedVersionRef())
                    && decision.expiresAt() != null && decision.expiresAt().isAfter(now);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public static String cookieName(String studyRef) {
        return "OTCMS_VIEW_GRANT_" + sha256(studyRef).substring(0, 16);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedGrant(String cookieName, String token, Instant expiresAt) { }
}
