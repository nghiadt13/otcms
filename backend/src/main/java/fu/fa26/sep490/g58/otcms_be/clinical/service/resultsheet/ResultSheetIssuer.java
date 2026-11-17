package fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalMedicalDocumentPayloadRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalMedicalDocumentRepository;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessages;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Issues the initial immutable snapshot and its notification atomically. */
@Service
public class ResultSheetIssuer {
    private final VisitReviewSourceService visits;
    private final ResultSheetSnapshotAssembler snapshots;
    private final ResultSheetPdfRenderer renderer;
    private final ClinicalMedicalDocumentRepository documents;
    private final ClinicalMedicalDocumentPayloadRepository payloads;
    private final NotificationPublisher notifications;
    private final Clock clock;

    public ResultSheetIssuer(VisitReviewSourceService visits, ResultSheetSnapshotAssembler snapshots,
            ResultSheetPdfRenderer renderer, ClinicalMedicalDocumentRepository documents,
            ClinicalMedicalDocumentPayloadRepository payloads, NotificationPublisher notifications, Clock clock) {
        this.visits = visits; this.snapshots = snapshots; this.renderer = renderer; this.documents = documents;
        this.payloads = payloads; this.notifications = notifications; this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void issue(UUID visitId, UUID doctorId) {
        var visit = visits.find(visitId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        if (!"CLOSED".equals(visit.status())) throw new ClinicalOperationException("MSG-42");
        var existing = documents.findByVisitIdAndDocumentTypeOrderByCreatedAtDescIdDesc(visitId,
                fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT);
        if (existing.stream().anyMatch(document -> "ACTIVE".equals(document.getStatus()))) return;
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var snapshot = snapshots.assemble(visitId, doctorId, now);
        byte[] pdf = renderer.render(snapshot);
        UUID documentId = UUID.randomUUID();
        // A competing transaction may have won after the first read; only the winner publishes.
        if (documents.insertGeneratedResultSheet(documentId, visitId, visit.patientProfileId(), pdf.length, now) == 0) return;
        if (payloads.put(documentId, pdf) != 1 || documents.releaseGeneratedResultSheet(documentId, doctorId, now) != 1) {
            throw new ClinicalOperationException("DEPENDENCY_UNAVAILABLE");
        }
        notifications.publishToPatient(visit.patientProfileId(),
                NotificationMessages.visitResultsPublished(visitId, visit.arrivedAt(), snapshot.patientName()));
    }
}
