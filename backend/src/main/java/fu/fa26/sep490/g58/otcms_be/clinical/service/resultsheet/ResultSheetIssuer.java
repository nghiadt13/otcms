package fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalMedicalDocumentType;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentContentStore;
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

/** Issues immutable result sheet versions (initial and amended) together with their notification. */
@Service
public class ResultSheetIssuer {
    private final VisitReviewSourceService visits;
    private final ResultSheetSnapshotAssembler snapshots;
    private final ResultSheetPdfRenderer renderer;
    private final ClinicalMedicalDocumentRepository documents;
    private final ClinicalMedicalDocumentContentStore contents;
    private final NotificationPublisher notifications;
    private final Clock clock;

    public ResultSheetIssuer(VisitReviewSourceService visits, ResultSheetSnapshotAssembler snapshots,
            ResultSheetPdfRenderer renderer, ClinicalMedicalDocumentRepository documents,
            ClinicalMedicalDocumentContentStore contents, NotificationPublisher notifications, Clock clock) {
        this.visits = visits; this.snapshots = snapshots; this.renderer = renderer; this.documents = documents;
        this.contents = contents; this.notifications = notifications; this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void issue(UUID visitId, UUID doctorId) {
        var visit = visits.find(visitId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        if (!"CLOSED".equals(visit.status())) throw new ClinicalOperationException("MSG-42");
        var existing = documents.findByVisitIdAndDocumentTypeOrderByCreatedAtDescIdDesc(visitId,
                ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT);
        if (existing.stream().anyMatch(document -> "ACTIVE".equals(document.getStatus()))) return;
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var snapshot = snapshots.assemble(visitId, doctorId, now, 1);
        byte[] pdf = renderer.render(snapshot);
        UUID documentId = UUID.randomUUID();
        // A competing transaction may have won after the first read; only the winner publishes.
        if (documents.insertGeneratedResultSheet(documentId, visitId, visit.patientProfileId(), pdf.length, now, 1) == 0) return;
        if (!contents.put(visitId, documentId, "application/pdf", pdf) || documents.releaseGeneratedResultSheet(documentId, doctorId, now) != 1) {
            throw new ClinicalOperationException("DEPENDENCY_UNAVAILABLE");
        }
        notifications.publishToPatient(visit.patientProfileId(),
                NotificationMessages.visitResultsPublished(visitId, visit.arrivedAt(), snapshot.patientName()));
    }

    /**
     * Issues the next version after an amendment. Joins the amendment transaction so that the data change,
     * the audit entry, the replaced version and the new version commit or roll back together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long reissue(UUID visitId, UUID doctorId) {
        var visit = visits.find(visitId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        if (!"CLOSED".equals(visit.status())) throw new ClinicalOperationException("MSG-42");
        var current = documents.findByVisitIdAndDocumentTypeOrderByCreatedAtDescIdDesc(visitId,
                ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT).stream()
                .filter(document -> "ACTIVE".equals(document.getStatus())).findFirst()
                .orElseThrow(() -> new ClinicalOperationException("MSG-42"));
        long version = current.getVersion() + 1;
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        // Retire first: a concurrent amendment finds no ACTIVE row and is rejected instead of forking the chain.
        if (documents.markResultSheetReplaced(current.getId(), now) != 1) throw new ClinicalOperationException("MSG-42");
        var snapshot = snapshots.assemble(visitId, doctorId, now, version);
        byte[] pdf = renderer.render(snapshot);
        UUID documentId = UUID.randomUUID();
        if (documents.insertGeneratedResultSheet(documentId, visitId, visit.patientProfileId(), pdf.length, now, version) != 1
                || !contents.put(visitId, documentId, "application/pdf", pdf)
                || documents.releaseGeneratedResultSheet(documentId, doctorId, now) != 1) {
            throw new ClinicalOperationException("DEPENDENCY_UNAVAILABLE");
        }
        notifications.publishToPatient(visit.patientProfileId(),
                NotificationMessages.resultSheetNewVersion(visitId, visit.arrivedAt(), snapshot.patientName()));
        return version;
    }
}
