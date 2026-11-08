package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PastMedicalRecordsDtos.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentHistoryQueryService.DocumentSummary;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionHistoryQueryService.IssuedPrescriptionSummary;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticPatientHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitPatientHistoryQueryService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ClinicalPastMedicalRecordsServiceImpl implements ClinicalPastMedicalRecordsService {
    private final DoctorAccountAccessService accounts;
    private final VisitClinicalContextPort contexts;
    private final VisitPatientHistoryQueryService visits;
    private final ClinicalExaminationHistoryQueryService examinations;
    private final ClinicalPrescriptionHistoryQueryService prescriptions;
    private final ClinicalMedicalDocumentHistoryQueryService documents;
    private final DiagnosticPatientHistoryQueryService diagnostic;

    public ClinicalPastMedicalRecordsServiceImpl(DoctorAccountAccessService accounts, VisitClinicalContextPort contexts,
            VisitPatientHistoryQueryService visits, ClinicalExaminationHistoryQueryService examinations,
            ClinicalPrescriptionHistoryQueryService prescriptions, ClinicalMedicalDocumentHistoryQueryService documents,
            DiagnosticPatientHistoryQueryService diagnostic) {
        this.accounts = accounts; this.contexts = contexts; this.visits = visits; this.examinations = examinations;
        this.prescriptions = prescriptions; this.documents = documents; this.diagnostic = diagnostic;
    }

    // Repeatable snapshot for all owners. Only the existing Diagnostic access audit is inserted;
    // no clinical/Visit/document/prescription business data is changed.
    @Override @Transactional(isolation = Isolation.REPEATABLE_READ)
    public HistoryResponse getPastMedicalRecords(UUID currentVisitId, UUID doctorId, UUID accountId, int page, int size) {
        if (currentVisitId == null || page < 0 || size < 1 || size > 50) throw error("MSG-39");
        if (doctorId == null || accountId == null || !accounts.isActiveDoctorAccount(accountId)) throw error("MSG-40");
        var context = contexts.getVisitContext(currentVisitId);
        if (context == null) throw error("MSG-41");
        if (!doctorId.equals(context.responsibleDoctorId())) throw error("MSG-40");
        if (!context.clinicalWorkAllowed()) throw error("MSG-42");
        if (!currentVisitId.equals(context.visitId()) || context.patientId() == null) throw error("DEPENDENCY_UNAVAILABLE");
        UUID patientId = context.patientId();
        VisitPatientHistoryQueryService.HistoryPage history;
        try {
            history = visits.findPastVisits(patientId, currentVisitId, page, size);
        } catch (VisitOperationException exception) {
            throw error(switch (exception.code()) {
                case "RESOURCE_NOT_FOUND" -> "MSG-41";
                case "ACCESS_DENIED" -> "MSG-40";
                case "VALIDATION_FAILED" -> "MSG-39";
                default -> "DEPENDENCY_UNAVAILABLE";
            });
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (var visit : history.items()) {
            if (!patientId.equals(visit.patientProfileId()) || currentVisitId.equals(visit.visitId())
                    || !"CLOSED".equals(visit.status()) || !ids.add(visit.visitId())) throw error("DEPENDENCY_UNAVAILABLE");
        }
        if (ids.size() > size || history.total() < ids.size()) throw error("DEPENDENCY_UNAVAILABLE");
        var exams = examinations.findCompletedByVisitIds(ids);
        for (var entry : exams.entrySet()) {
            if (!ids.contains(entry.getKey()) || !entry.getKey().equals(entry.getValue().visitId())) throw error("DEPENDENCY_UNAVAILABLE");
        }
        Map<UUID, IssuedPrescriptionSummary> rx = new HashMap<>();
        for (var prescription : prescriptions.findIssuedByPatientAndVisitIds(patientId, ids)) {
            requireScope(patientId, ids, prescription.patientProfileId(), prescription.visitId());
            if (prescription.issuedAt() == null || rx.put(prescription.visitId(), prescription) != null) throw error("DEPENDENCY_UNAVAILABLE");
        }
        Map<UUID, List<DocumentSummary>> docs = new HashMap<>();
        for (var document : documents.findReleased(patientId, ids)) {
            requireScope(patientId, ids, document.patientProfileId(), document.visitId());
            if (!document.released()) throw error("DEPENDENCY_UNAVAILABLE");
            docs.computeIfAbsent(document.visitId(), ignored -> new ArrayList<>()).add(document);
        }
        Map<UUID, List<DiagnosticHistoryItem>> images = new HashMap<>();
        for (var image : diagnostic.findReleased(patientId, ids, accountId, page, size)) {
            requireScope(patientId, ids, image.patientProfileId(), image.result().visitId());
            images.computeIfAbsent(image.result().visitId(), ignored -> new ArrayList<>()).add(image.result());
        }
        var items = history.items().stream().map(v -> {
            var exam = exams.get(v.visitId());
            return new HistoricalVisitRecord(v.visitId(), v.arrivedAt(), exam == null ? null : exam.examination(),
                    exam == null ? null : exam.advice(), rx.get(v.visitId()), docs.getOrDefault(v.visitId(), List.of()),
                    images.getOrDefault(v.visitId(), List.of()));
        }).toList();
        return new HistoryResponse(currentVisitId, patientId, items, page, size, history.total());
    }
    private static void requireScope(UUID patientId, Set<UUID> ids, UUID childPatientId, UUID childVisitId) {
        if (!patientId.equals(childPatientId) || !ids.contains(childVisitId)) throw error("DEPENDENCY_UNAVAILABLE");
    }
    private static ClinicalOperationException error(String code) { return new ClinicalOperationException(code); }
}
