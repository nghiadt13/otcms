package fu.fa26.sep490.g58.otcms_be.visit.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReleaseAcknowledgement;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitImagingFindingsReleasePort;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitImagingFindingsReleaseJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitImagingFindingsReleaseRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persists Visit's release receipt so duplicate outbox deliveries are safe and auditable. */
@Component
public class VisitImagingFindingsReleaseAdapter implements VisitImagingFindingsReleasePort {
    private final ClinicVisitJpaRepository visits;
    private final VisitImagingFindingsReleaseRepository releases;

    public VisitImagingFindingsReleaseAdapter(ClinicVisitJpaRepository visits,
            VisitImagingFindingsReleaseRepository releases) {
        this.visits = visits;
        this.releases = releases;
    }

    @Override
    @Transactional
    public ReleaseAcknowledgement publishFindingsReleased(FindingsReleaseEvent event, String payloadSha256) {
        if (event == null || event.eventId() == null || event.requestId() == null || event.visitId() == null
                || event.patientId() == null || event.studyRef() == null || event.studyRef().isBlank()
                || event.findingsVersionId() == null || event.reportId() == null
                || !isSha256(event.reportSha256()) || !isSha256(payloadSha256)
                || !payloadSha256.equals(hashRelease(event))) {
            throw new IllegalArgumentException("Invalid imaging findings release event");
        }
        var existing = releases.findByEventId(event.eventId()).orElse(null);
        if (existing != null) {
            if (!payloadSha256.equals(existing.getPayloadSha256())) {
                throw new IllegalStateException("Imaging findings release event payload conflict");
            }
            return new ReleaseAcknowledgement(event.eventId(), event.requestId(), payloadSha256, true);
        }
        var visit = visits.findById(event.visitId()).orElseThrow(
                () -> new IllegalStateException("Imaging findings release Visit not found"));
        if (!event.patientId().equals(visit.getPatientProfileId())) {
            throw new IllegalStateException("Imaging findings release Patient does not match Visit");
        }
        releases.saveAndFlush(new VisitImagingFindingsReleaseJpaEntity(UUID.randomUUID(), event.eventId(),
                event.requestId(), event.visitId(), event.patientId(), event.studyRef(),
                event.findingsVersionId(), event.reportId(), event.reportSha256(), payloadSha256));
        return new ReleaseAcknowledgement(event.eventId(), event.requestId(), payloadSha256, true);
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static String hashRelease(FindingsReleaseEvent event) {
        String canonical = String.join("\n", event.eventId().toString(), event.requestId().toString(),
                event.visitId().toString(), event.patientId().toString(), event.studyRef(),
                event.findingsVersionId().toString(), event.reportId().toString(), event.reportSha256());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
