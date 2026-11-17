package fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.ResultSheetImagingQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Composes an immutable view of the clinical data at the moment the sheet is issued. */
@Service
public class ResultSheetSnapshotAssembler {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);
    private final VisitReviewSourceService visits;
    private final PatientProfileDirectory patients;
    private final ClinicalExaminationHistoryQueryService examinations;
    private final ClinicalPrescriptionHistoryQueryService prescriptions;
    private final ResultSheetImagingQueryService imaging;
    private final DoctorProfileIdentityQueryService doctors;

    public ResultSheetSnapshotAssembler(VisitReviewSourceService visits, PatientProfileDirectory patients,
            ClinicalExaminationHistoryQueryService examinations, ClinicalPrescriptionHistoryQueryService prescriptions,
            ResultSheetImagingQueryService imaging, DoctorProfileIdentityQueryService doctors) {
        this.visits = visits; this.patients = patients; this.examinations = examinations;
        this.prescriptions = prescriptions; this.imaging = imaging; this.doctors = doctors;
    }

    public ResultSheetPdfRenderer.Snapshot assemble(UUID visitId, UUID doctorId, Instant issuedAt) {
        var visit = visits.find(visitId).orElseThrow();
        var patient = patients.findProfile(visit.patientProfileId()).orElseThrow();
        var completed = examinations.findCompletedByVisitIds(List.of(visitId)).get(visitId);
        if (completed == null) throw new IllegalStateException("Completed examination missing");
        var exam = completed.examination();
        var advice = completed.advice();
        List<String> lines = new ArrayList<>();
        lines.add("Mã bệnh nhân: " + patient.id());
        lines.add("Ngày khám: " + (visit.arrivedAt() == null ? "—" : TIME.format(visit.arrivedAt())));
        lines.add("Tuổi: " + (patient.dateOfBirth() == null ? "—" : Period.between(patient.dateOfBirth(), issuedAt.atZone(ZONE).toLocalDate()).getYears()));
        lines.add("Giới tính: " + gender(patient.gender()));
        lines.add("I. KHÁM BỆNH");
        lines.add("Lý do khám: " + value(exam.reasonForVisit()));
        lines.add("Triệu chứng: " + value(exam.symptoms()));
        lines.add("Khám lâm sàng: " + value(exam.examinationFindings()));
        lines.add("Bộ phận: " + value(exam.injuryBodySite()));
        lines.add("Bên: " + value(exam.injurySide()));
        lines.add("Cơ chế: " + value(exam.injuryMechanism()));
        lines.add("Chẩn đoán ICD-10: " + (exam.diagnoses().isEmpty() ? "—" : String.join(", ", exam.diagnoses().stream().map(d -> d.code()).toList())));
        lines.add("II. CẬN LÂM SÀNG");
        lines.add("Xét nghiệm: —");
        var images = imaging.findReleased(visit.patientProfileId(), visitId);
        if (images.isEmpty()) lines.add("Chẩn đoán hình ảnh: —");
        for (var image : images) lines.add("Chẩn đoán hình ảnh " + value(image.imagingType()) + " (" + value(image.bodyArea()) + "): " + value(image.findingsText()));
        lines.add("III. ĐƠN THUỐC");
        var issued = prescriptions.findIssuedByVisit(visitId);
        if (issued.isEmpty()) lines.add("—");
        for (var prescription : issued) for (var item : prescription.items()) {
            lines.add(item.lineNumber() + ". " + value(item.medicine()) + " | " + value(item.dose()) + " | "
                    + value(item.frequency()) + " | " + value(item.usage()));
        }
        lines.add("Lời dặn chăm sóc: " + value(advice.homeCareAdvice()));
        lines.add("Hoạt động: " + value(advice.activityAdvice()));
        lines.add("Phục hồi chức năng: " + value(advice.rehabilitationAdvice()));
        lines.add("Dấu hiệu cảnh báo: " + value(advice.warningSigns()));
        lines.add("Tái khám: " + value(advice.followUpRecommendation()));
        lines.add("Bác sĩ: " + value(doctors.findDoctorNames(List.of(doctorId)).get(doctorId)));
        lines.add("Giờ phát hành: " + TIME.format(issuedAt));
        lines.add("Phiên bản: 1");
        return new ResultSheetPdfRenderer.Snapshot(value(patient.fullName()), lines);
    }

    private static String value(String text) { return text == null || text.isBlank() ? "—" : text; }
    private static String gender(String gender) {
        if (gender == null) return "—";
        return switch (gender.toUpperCase(java.util.Locale.ROOT)) {
            case "MALE" -> "Nam";
            case "FEMALE" -> "Nữ";
            default -> "Khác";
        };
    }
}
