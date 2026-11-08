package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticHistoryItem;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.DiagnosticHistoryAccessAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticHistoryAccessAuditJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticPatientHistoryQueryService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticPatientHistoryQueryServiceImpl implements DiagnosticPatientHistoryQueryService {
    private final DiagnosticRequestJpaRepository requests;
    private final DiagnosticHistoryAccessAuditJpaRepository audits;
    public DiagnosticPatientHistoryQueryServiceImpl(DiagnosticRequestJpaRepository requests, DiagnosticHistoryAccessAuditJpaRepository audits) {
        this.requests = requests; this.audits = audits;
    }
    /** Clinical business records are read-only; preserves the existing mandatory history access audit. */
    @Override @Transactional
    public List<HistoricalImagingResult> findReleased(UUID patientId, Collection<UUID> visitIds, UUID actorId, int page, int size) {
        if (patientId == null || actorId == null || visitIds == null || visitIds.size() > 50
                || visitIds.stream().anyMatch(Objects::isNull) || page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Invalid history query scope");
        }
        var results = visitIds.isEmpty() ? List.<HistoricalImagingResult>of()
                : requests.findReleasedPatientHistory(patientId, visitIds,
                        List.of(DiagnosticRequestStatus.RESULT_AVAILABLE, DiagnosticRequestStatus.REVIEWED)).stream()
                    .map(r -> new HistoricalImagingResult(r.getPatientId(), new DiagnosticHistoryItem(r.getRequestId(),
                            r.getVisitId(), r.getImagingType(), r.getStatus(), r.getStudyRef(), r.getFindingsVersion(),
                            r.getFindingsText(), r.getReportId()))).toList();
        audits.save(new DiagnosticHistoryAccessAuditJpaEntity(UUID.randomUUID(), actorId, DiagnosticHistoryScope.PATIENT,
                patientId, page, size, results.size()));
        return results;
    }
}
