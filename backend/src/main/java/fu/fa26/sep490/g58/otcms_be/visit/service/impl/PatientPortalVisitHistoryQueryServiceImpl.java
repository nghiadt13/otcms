package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.PatientPortalVisitHistoryQueryService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PatientPortalVisitHistoryQueryServiceImpl implements PatientPortalVisitHistoryQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private final ClinicVisitJpaRepository visits;

    public PatientPortalVisitHistoryQueryServiceImpl(ClinicVisitJpaRepository visits) {
        this.visits = visits;
    }

    @Override
    public VisitPage findVisits(List<UUID> patientProfileIds, int page, int size) {
        validate(patientProfileIds, page, size);
        if (patientProfileIds.isEmpty()) return new VisitPage(List.of(), page, size, 0, 0);
        var result = visits.findPatientPortalHistory(patientProfileIds, PageRequest.of(page, size));
        return new VisitPage(result.getContent().stream().map(PatientPortalVisitHistoryQueryServiceImpl::map).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public Optional<PatientVisit> findVisit(UUID visitId, List<UUID> patientProfileIds) {
        if (visitId == null) throw new IllegalArgumentException("Visit ID is required");
        validate(patientProfileIds, 0, 1);
        if (patientProfileIds.isEmpty()) return Optional.empty();
        return visits.findPatientPortalVisit(visitId, patientProfileIds).map(PatientPortalVisitHistoryQueryServiceImpl::map);
    }

    @Override
    public Optional<UUID> findPreviousVisit(PatientVisit visit) {
        requireVisit(visit);
        return visits.findPatientPortalPreviousVisit(visit.patientProfileId(), visit.arrivedAt(), visit.visitId(),
                PageRequest.of(0, 1)).stream().findFirst();
    }

    @Override
    public Optional<UUID> findNextVisit(PatientVisit visit) {
        requireVisit(visit);
        return visits.findPatientPortalNextVisit(visit.patientProfileId(), visit.arrivedAt(), visit.visitId(),
                PageRequest.of(0, 1)).stream().findFirst();
    }

    private static PatientVisit map(ClinicVisitJpaEntity visit) {
        return new PatientVisit(visit.getId(), visit.getPatientProfileId(), visit.getArrivedAt(), visit.getStatus(),
                visit.getSource(), visit.getOriginatingAppointmentId());
    }

    private static void validate(List<UUID> patientProfileIds, int page, int size) {
        if (patientProfileIds == null || page < 0 || size < 1 || size > MAX_PAGE_SIZE
                || patientProfileIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Invalid patient visit history scope");
        }
    }

    private static void requireVisit(PatientVisit visit) {
        if (visit == null || visit.visitId() == null || visit.patientProfileId() == null || visit.arrivedAt() == null) {
            throw new IllegalArgumentException("A complete patient visit is required");
        }
    }
}
