package fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.clinical.model.DiagnosisType;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationHistoryQueryService.Advice;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationHistoryQueryService.CompletedRecord;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationHistoryQueryService.Diagnosis;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationHistoryQueryService.Examination;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.ResultSheetImagingQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReviewSourceService.Source;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** NF-02 section I: body site, side, mechanism, description and treatment directions (UC37, UC42). */
class ResultSheetSnapshotAssemblerTest {
    private final UUID visitId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final VisitReviewSourceService visits = mock(VisitReviewSourceService.class);
    private final PatientProfileDirectory patients = mock(PatientProfileDirectory.class);
    private final ClinicalExaminationHistoryQueryService examinations = mock(ClinicalExaminationHistoryQueryService.class);
    private final ClinicalPrescriptionHistoryQueryService prescriptions = mock(ClinicalPrescriptionHistoryQueryService.class);
    private final ResultSheetImagingQueryService imaging = mock(ResultSheetImagingQueryService.class);
    private final DoctorProfileIdentityQueryService doctors = mock(DoctorProfileIdentityQueryService.class);

    @Test
    void sheetPrintsInjuryFieldsWithSideLabelAndTreatmentDirections() {
        var examination = new Examination("Đau gối", "Đau khi đi", "Tràn dịch", "Gối", "LEFT", "Ngã",
                List.of(new Diagnosis("M17.1", DiagnosisType.PRIMARY)), "Sưng và đau khi gập",
                List.of("HOME_CARE", "PROCEDURE_ELSEWHERE"));
        var lines = assemble(examination, new Advice("Chườm lạnh", null, null, null, "Tái khám sau 2 tuần", true));

        assertTrue(lines.contains("Bộ phận: Gối"), lines.toString());
        assertTrue(lines.contains("Bên: Trái"), lines.toString());
        assertTrue(lines.contains("Cơ chế: Ngã"), lines.toString());
        assertTrue(lines.contains("Mô tả chấn thương: Sưng và đau khi gập"), lines.toString());
        assertTrue(lines.contains("Hướng điều trị: Chăm sóc tại nhà, Thủ thuật hoặc phẫu thuật tại cơ sở khác"), lines.toString());
        assertTrue(lines.contains("Lời dặn chăm sóc: Chườm lạnh"), lines.toString());
    }

    @Test
    void unrecordedInjuryFieldsPrintDashes() {
        var examination = new Examination("Đau", "Đau", "Sưng", null, null, null, List.of(), null, null);
        var lines = assemble(examination, new Advice(null, null, null, null, null, null));

        assertTrue(lines.contains("Bộ phận: —"), lines.toString());
        assertTrue(lines.contains("Bên: —"), lines.toString());
        assertTrue(lines.contains("Cơ chế: —"), lines.toString());
        assertTrue(lines.contains("Mô tả chấn thương: —"), lines.toString());
        assertTrue(lines.contains("Hướng điều trị: —"), lines.toString());
    }

    private List<String> assemble(Examination examination, Advice advice) {
        when(visits.find(visitId)).thenReturn(Optional.of(new Source(visitId, patientId, "COMPLETED",
                Instant.parse("2026-11-18T01:00:00Z"), "BS Hương", "Khám chỉnh hình")));
        when(patients.findProfile(patientId)).thenReturn(Optional.of(new ProfileRecord(patientId, null,
                "Nguyễn Thị Ánh", null, null, LocalDate.of(1990, 1, 1), "FEMALE")));
        when(examinations.findCompletedByVisitIds(List.of(visitId)))
                .thenReturn(Map.of(visitId, new CompletedRecord(visitId, examination, advice)));
        when(imaging.findReleased(patientId, visitId)).thenReturn(List.of());
        when(prescriptions.findIssuedByVisit(visitId)).thenReturn(List.of());
        when(doctors.findDoctorNames(List.of(doctorId))).thenReturn(Map.of(doctorId, "BS Hương"));
        var assembler = new ResultSheetSnapshotAssembler(visits, patients, examinations, prescriptions, imaging, doctors);
        return assembler.assemble(visitId, doctorId, Instant.parse("2026-11-18T03:00:00Z"), 1L).lines();
    }
}
