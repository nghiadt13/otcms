package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentReviewContextService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PatientVisitResultDtos.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticReleasedReportService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.CareRelationshipException;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.ProfileItem;
import fu.fa26.sep490.g58.otcms_be.patient.service.DependentProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService;
import fu.fa26.sep490.g58.otcms_be.visit.service.PatientPortalVisitHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.PatientPortalVisitHistoryQueryService.PatientVisit;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientVisitResultQueryServiceImpl implements PatientVisitResultQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PatientProfileAccessService access;
    private final DependentProfileService dependents;
    private final PatientProfileDirectory profiles;
    private final PatientPortalVisitHistoryQueryService visits;
    private final ClinicalExaminationHistoryQueryService examinations;
    private final ClinicalPrescriptionHistoryQueryService prescriptions;
    private final ClinicalMedicalDocumentHistoryQueryService documents;
    private final DiagnosticReleasedReportService diagnosticReports;
    private final AppointmentReviewContextService appointments;

    public PatientVisitResultQueryServiceImpl(PatientProfileAccessService access, DependentProfileService dependents,
            PatientProfileDirectory profiles, PatientPortalVisitHistoryQueryService visits,
            ClinicalExaminationHistoryQueryService examinations, ClinicalPrescriptionHistoryQueryService prescriptions,
            ClinicalMedicalDocumentHistoryQueryService documents, DiagnosticReleasedReportService diagnosticReports,
            AppointmentReviewContextService appointments) {
        this.access = access;
        this.dependents = dependents;
        this.profiles = profiles;
        this.visits = visits;
        this.examinations = examinations;
        this.prescriptions = prescriptions;
        this.documents = documents;
        this.diagnosticReports = diagnosticReports;
        this.appointments = appointments;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public VisitResultPage list(UUID accountId, UUID requestedPatientId, int page, int size) {
        requirePage(accountId, page, size);
        Scope scope = scope(accountId, requestedPatientId);
        var result = visits.findVisits(scope.profileIds(), page, size);
        Map<UUID, ProfileRecord> profilesById = profileMap(scope.profileIds());
        List<VisitSummary> items = result.items().stream().map(visit -> {
            ProfileRecord profile = requireProfile(profilesById, visit.patientProfileId());
            return new VisitSummary(visit.visitId(), visitDate(visit), resultStatus(visit), serviceName(visit),
                    patientRef(profile, scope.relationships()));
        }).toList();
        return new VisitResultPage(items, result.page(), result.size(), result.totalItems(), result.totalPages());
    }

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public VisitResult get(UUID accountId, UUID visitId) {
        if (accountId == null || visitId == null) throw error("MSG-39");
        Scope scope = scope(accountId, null);
        PatientVisit visit = visits.findVisit(visitId, scope.profileIds()).orElseThrow(() -> error("MSG-41"));
        ProfileRecord profile = requireProfile(profileMap(List.of(visit.patientProfileId())), visit.patientProfileId());
        boolean completed = "CLOSED".equals(visit.status());

        ExaminationSection exam = null;
        if (completed) {
            var completedExam = examinations.findCompletedByVisitIds(List.of(visitId)).get(visitId);
            if (completedExam != null) exam = examination(completedExam);
        }

        var releasedImaging = diagnosticReports.listForPatientVisit(
                DiagnosticActor.user(accountId, AccountRole.PATIENT), visitId);
        List<ImagingItem> images = releasedImaging.stream().filter(item -> "CURRENT".equals(item.status()))
                .map(item -> new ImagingItem(item.studyRef(), imagingLabel(item.imagingType(), item.bodyArea()),
                        item.findingsText())).toList();

        List<PrescriptionRef> issued = completed
                ? prescriptions.findIssuedByPatientAndVisitIds(visit.patientProfileId(), List.of(visitId)).stream()
                    .map(item -> new PrescriptionRef(item.id(), item.issuedAt(), item.items().size())).toList()
                : List.of();

        AdministrativeInfo administrative = administrative(profile);
        List<ResultSheet> sheets = new ArrayList<>();
        if (completed) {
            documents.findReleased(visit.patientProfileId(), List.of(visitId)).stream()
                    .filter(document -> "CLINIC_EXAMINATION_REPORT".equals(document.documentType())
                            && "application/pdf".equalsIgnoreCase(document.mediaType()))
                    .map(document -> new ResultSheet(document.id(), 1, "CURRENT", document.releasedAt(), document.title(),
                            "/api/v1/medical-documents/" + document.id() + "/view"))
                    .forEach(sheets::add);
        }
        releasedImaging.stream().filter(report -> "CURRENT".equals(report.status()))
                .map(report -> new ResultSheet(report.reportId(), report.version(), report.status(), report.releasedAt(),
                        imagingLabel(report.imagingType(), report.bodyArea()), "/api/v1/imaging-history/visits/" + visitId + "/reports/"
                                + report.reportId() + "/content"))
                .forEach(sheets::add);
        releasedImaging.stream().filter(report -> "REPLACED".equals(report.status()))
                .map(report -> new ResultSheet(report.reportId(), report.version(), report.status(), report.releasedAt(),
                        imagingLabel(report.imagingType(), report.bodyArea()), "/api/v1/imaging-history/visits/" + visitId + "/reports/"
                                + report.reportId() + "/content"))
                .forEach(sheets::add);
        sheets.sort(Comparator.comparing(ResultSheet::issuedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        List<String> visible = new ArrayList<>();
        if (exam != null) visible.add("EXAMINATION");
        if (!images.isEmpty()) visible.add("IMAGING");
        if (!issued.isEmpty()) visible.add("PRESCRIPTIONS");
        if (administrative != null) visible.add("ADMINISTRATIVE");
        if (!sheets.isEmpty()) visible.add("RESULT_SHEET");

        return new VisitResult(visitId, visitDate(visit), resultStatus(visit), patientRef(profile, scope.relationships()),
                visits.findPreviousVisit(visit).orElse(null), visits.findNextVisit(visit).orElse(null), visible,
                exam, images.isEmpty() ? null : images, issued.isEmpty() ? null : issued, administrative,
                sheets.isEmpty() ? null : sheets);
    }

    private Scope scope(UUID accountId, UUID requestedPatientId) {
        Set<UUID> accessible = access.accessibleProfileIds(accountId);
        if (accessible.isEmpty()) throw error("MSG-40");
        if (requestedPatientId != null && !accessible.contains(requestedPatientId)) throw error("MSG-40");
        Map<UUID, String> relationships = new HashMap<>();
        Set<UUID> authorizedProfiles = new LinkedHashSet<>();
        try {
            for (ProfileItem item : dependents.list(accountId).items()) {
                if ("CONFIRMED".equals(item.linkStatus()) && accessible.contains(item.patientProfileId())) {
                    relationships.put(item.patientProfileId(), item.relationship());
                    authorizedProfiles.add(item.patientProfileId());
                }
            }
        } catch (CareRelationshipException exception) {
            throw error("MSG-40");
        }
        if (authorizedProfiles.isEmpty()) throw error("MSG-40");
        if (requestedPatientId != null && !authorizedProfiles.contains(requestedPatientId)) throw error("MSG-40");
        List<UUID> ids = requestedPatientId == null ? authorizedProfiles.stream().sorted().toList() : List.of(requestedPatientId);
        return new Scope(ids, relationships);
    }

    private Map<UUID, ProfileRecord> profileMap(Collection<UUID> ids) {
        return profiles.findProfiles(ids).stream().collect(Collectors.toMap(ProfileRecord::id, Function.identity()));
    }

    private static ProfileRecord requireProfile(Map<UUID, ProfileRecord> profiles, UUID id) {
        ProfileRecord profile = profiles.get(id);
        if (profile == null) throw error("DEPENDENCY_UNAVAILABLE");
        return profile;
    }

    private String serviceName(PatientVisit visit) {
        if (visit.originatingAppointmentId() != null) {
            String name = appointments.find(visit.originatingAppointmentId()).map(AppointmentReviewContextService.Context::serviceName)
                    .filter(value -> value != null && !value.isBlank()).orElse(null);
            if (name != null) return name;
        }
        return "Khám tại phòng khám";
    }

    private static PatientRef patientRef(ProfileRecord profile, Map<UUID, String> relationships) {
        String relationship = relationships.getOrDefault(profile.id(), "SELF");
        return new PatientRef(profile.id(), profile.fullName(), relationship);
    }

    private static ExaminationSection examination(ClinicalExaminationHistoryQueryService.CompletedRecord record) {
        var source = record.examination();
        List<Icd10Ref> primary = source.diagnoses().stream().filter(d -> d.type().name().equals("PRIMARY"))
                .map(d -> new Icd10Ref(d.code(), "")).toList();
        List<Icd10Ref> secondary = source.diagnoses().stream().filter(d -> d.type().name().equals("SECONDARY"))
                .map(d -> new Icd10Ref(d.code(), "")).toList();
        var advice = record.advice();
        String direction = join(advice.homeCareAdvice(), advice.activityAdvice(), advice.rehabilitationAdvice());
        String warnings = join(advice.warningSigns());
        String followUpNote = null;
        if (Boolean.TRUE.equals(advice.followUpIndicated())) {
            followUpNote = advice.followUpRecommendation() == null || advice.followUpRecommendation().isBlank()
                    ? "Bác sĩ có hẹn tái khám nhưng chưa ghi ngày."
                    : advice.followUpRecommendation();
        }
        return new ExaminationSection(value(source.reasonForVisit()), value(source.symptoms()), value(source.examinationFindings()),
                primary, secondary, direction, warnings, null, followUpNote);
    }

    private static AdministrativeInfo administrative(ProfileRecord profile) {
        String gender = profile.gender() == null ? "OTHER" : switch (profile.gender().toUpperCase(Locale.ROOT)) {
            case "MALE" -> "MALE";
            case "FEMALE" -> "FEMALE";
            default -> "OTHER";
        };
        return new AdministrativeInfo(profile.fullName(), profile.dateOfBirth(), gender, null,
                profile.phoneCanonical(), profile.address());
    }

    private static java.time.LocalDate visitDate(PatientVisit visit) {
        return visit.arrivedAt().atZone(CLINIC_ZONE).toLocalDate();
    }

    private static String resultStatus(PatientVisit visit) {
        return "CLOSED".equals(visit.status()) ? "COMPLETED" : "INCOMPLETE";
    }

    private static String value(String text) { return text == null ? "" : text; }
    private static String imagingLabel(String imagingType, String bodyArea) {
        String type = imagingType == null ? "" : imagingType.trim();
        String area = bodyArea == null ? "" : bodyArea.trim();
        if (type.isEmpty()) return area;
        return area.isEmpty() ? type : type + " · " + area;
    }
    private static String join(String... parts) {
        return Arrays.stream(parts).filter(value -> value != null && !value.isBlank()).collect(Collectors.joining("; "));
    }
    private static void requirePage(UUID accountId, int page, int size) {
        if (accountId == null || page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw error("MSG-39");
    }
    private static ClinicalOperationException error(String code) { return new ClinicalOperationException(code); }
    private record Scope(List<UUID> profileIds, Map<UUID, String> relationships) { }
}
