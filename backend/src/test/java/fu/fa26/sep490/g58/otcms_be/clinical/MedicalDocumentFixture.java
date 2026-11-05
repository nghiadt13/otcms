package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.security.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalMedicalDocumentServiceImpl;
import fu.fa26.sep490.g58.otcms_be.clinical.validation.ClinicalMedicalDocumentFileValidator;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.State;
import fu.fa26.sep490.g58.otcms_be.patient.model.entity.CareRelationshipJpaEntity;
import fu.fa26.sep490.g58.otcms_be.patient.repository.CareRelationshipRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.impl.PatientProfileAccessServiceImpl;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Repository doubles model CAS control flow only; actual DB transaction claims belong to PostgreSQL tests. */
class MedicalDocumentFixture {
    final UUID visitId = UUID.randomUUID(), patientId = UUID.randomUUID(), doctorId = UUID.randomUUID();
    final UUID doctorAccountId = UUID.randomUUID(), patientAccountId = UUID.randomUUID(), receptionistAccountId = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-05T03:00:00Z");
    final ClinicalMedicalDocumentRepository documents = mock(ClinicalMedicalDocumentRepository.class);
    final ClinicalMedicalDocumentPayloadRepository payloads = mock(ClinicalMedicalDocumentPayloadRepository.class);
    final ClinicalExaminationRepository examinations = mock(ClinicalExaminationRepository.class);
    final VisitClinicalContextPort clinicalVisits = mock(VisitClinicalContextPort.class);
    final VisitClinicalCareContextService visits = mock(VisitClinicalCareContextService.class);
    final VisitDoctorCareRelationQueryService care = mock(VisitDoctorCareRelationQueryService.class);
    final ReceptionistAccountAccessService receptionists = mock(ReceptionistAccountAccessService.class);
    final DoctorProfileIdentityQueryService doctors = mock(DoctorProfileIdentityQueryService.class);
    final PatientIdentityDirectory patientAccounts = mock(PatientIdentityDirectory.class);
    final PatientProfileDirectory profiles = mock(PatientProfileDirectory.class);
    final CareRelationshipRepository links = mock(CareRelationshipRepository.class);
    final ClinicalMedicalDocumentFileValidator validator = new ClinicalMedicalDocumentFileValidator();
    final ClinicalMedicalDocumentAccess access = new ClinicalMedicalDocumentAccess(new ClinicalDoctorIdentityResolver(doctors),
            clinicalVisits, visits, receptionists, new PatientProfileAccessServiceImpl(patientAccounts, profiles, links), care);
    final ClinicalMedicalDocumentService service = new ClinicalMedicalDocumentServiceImpl(access, documents, payloads, examinations,
            validator, Clock.fixed(now, ZoneOffset.UTC));
    final Map<UUID, Row> rows = new ConcurrentHashMap<>();
    final Map<UUID, byte[]> bytes = new ConcurrentHashMap<>();
    boolean open = true, allowed = true, exists = true;

    MedicalDocumentFixture() {
        when(doctors.findDoctorProfileId(doctorAccountId)).thenReturn(Optional.of(doctorId));
        when(receptionists.isActiveReceptionistAccount(receptionistAccountId)).thenReturn(true);
        when(clinicalVisits.getVisitContext(any())).thenAnswer(call -> exists ? new VisitClinicalContextPort.ClinicalVisitContext(
                call.getArgument(0), patientId, doctorId, open && allowed) : null);
        when(visits.findClinicalCareContext(any())).thenAnswer(call -> exists ? Optional.of(new VisitClinicalCareContextService.ClinicalCareContext(
                call.getArgument(0), patientId, allowed ? UUID.randomUUID() : null, allowed ? doctorId : null, open, open && allowed)) : Optional.empty());
        when(patientAccounts.isActivePatient(patientAccountId)).thenReturn(true);
        when(profiles.findPatientProfile(patientAccountId)).thenReturn(Optional.of(patientId));
        when(documents.insertDocument(any(), any(), any(), any(), any(), any(), any(), anyLong(), any())).thenAnswer(call -> {
            var row = new Row(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                    ClinicalMedicalDocumentType.valueOf(call.getArgument(3)), call.getArgument(4), call.getArgument(5),
                    call.getArgument(6), call.getArgument(7)); rows.put(row.id, row); return 1;
        });
        when(documents.findById(any())).thenAnswer(call -> { synchronized (rows) { return Optional.ofNullable(rows.get(call.getArgument(0))).map(Row::snapshot); } });
        when(documents.replace(any(), anyLong(), any(), any(), any(), anyLong(), any())).thenAnswer(call -> {
            synchronized (rows) {
                var row = rows.get(call.getArgument(0));
                if (row == null || row.released != null || row.version != (long) call.getArgument(1)) return 0;
                row.title = call.getArgument(2); row.filename = call.getArgument(3); row.media = call.getArgument(4); row.size = call.getArgument(5); row.version++; return 1;
            }
        });
        when(documents.release(any(), anyLong(), any(), any())).thenAnswer(call -> {
            synchronized (rows) {
                var row = rows.get(call.getArgument(0));
                if (row == null || row.released != null || row.version != (long) call.getArgument(1)) return 0;
                row.released = call.getArgument(3); row.version++; return 1;
            }
        });
        when(payloads.put(any(), any())).thenAnswer(call -> { bytes.put(call.getArgument(0), ((byte[]) call.getArgument(1)).clone()); return 1; });
        when(payloads.findById(any())).thenAnswer(call -> {
            byte[] data = bytes.get(call.getArgument(0));
            if (data == null) return Optional.empty();
            var payload = mock(ClinicalMedicalDocumentPayloadJpaEntity.class); when(payload.getContent()).thenReturn(data.clone()); return Optional.of(payload);
        });
        when(documents.findByVisitIdOrderByCreatedAtDescIdDesc(any())).thenAnswer(call -> selected(call.getArgument(0), null, false));
        when(documents.findByVisitIdAndReleasedAtIsNotNullOrderByCreatedAtDescIdDesc(any())).thenAnswer(call -> selected(call.getArgument(0), null, true));
        when(documents.findByVisitIdAndDocumentTypeOrderByCreatedAtDescIdDesc(any(), any())).thenAnswer(call -> selected(call.getArgument(0), call.getArgument(1), false));
    }
    List<ClinicalMedicalDocumentJpaEntity> selected(UUID visit, ClinicalMedicalDocumentType type, boolean released) {
        synchronized (rows) {
            return rows.values().stream().filter(row -> row.visit.equals(visit) && (type == null || row.type == type) && (!released || row.released != null))
                    .sorted(Comparator.comparing((Row row) -> row.id).reversed()).map(Row::snapshot).toList();
        }
    }
    AuthenticatedAccount doctor() { return new AuthenticatedAccount(doctorAccountId, AccountRole.DOCTOR, null); }
    AuthenticatedAccount patient() { return new AuthenticatedAccount(patientAccountId, AccountRole.PATIENT, null); }
    AuthenticatedAccount receptionist() { return new AuthenticatedAccount(receptionistAccountId, AccountRole.RECEPTIONIST, null); }
    DocumentView uploadReport() { return service.upload(doctor(), visitId, ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT, "Report", file("initial")); }
    DocumentFile file(String marker) { return new DocumentFile("report.pdf", "application/pdf", pdf(marker)); }
    void completed() {
        var examination = ClinicalExaminationJpaEntity.create(visitId, "reason", "symptoms", "findings", null, null, null, ClinicalExaminationStatus.COMPLETED, now);
        when(examinations.findByVisitId(visitId)).thenReturn(Optional.of(examination));
    }
    void dependent(boolean confirmed) {
        when(profiles.findPatientProfile(patientAccountId)).thenReturn(Optional.of(UUID.randomUUID()));
        if (confirmed) {
            var link = mock(CareRelationshipJpaEntity.class); when(link.getDependentPatientProfileId()).thenReturn(patientId);
            when(links.findAllByRequesterAccountIdAndState(patientAccountId, State.CONFIRMED)).thenReturn(List.of(link));
        }
    }
    static byte[] pdf(String marker) {
        StringBuilder text = new StringBuilder("%PDF-1.4\n%fixture-" + marker + "\n");
        String[] objects = {"<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>", "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] >>"};
        var offsets = new ArrayList<Integer>();
        for (int i = 0; i < objects.length; i++) { offsets.add(text.length()); text.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n"); }
        int xref = text.length(); text.append("xref\n0 4\n0000000000 65535 f \n");
        for (int offset : offsets) text.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        return text.append("trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n").toString().getBytes(StandardCharsets.US_ASCII);
    }
    class Row {
        final UUID id, visit, patient; final ClinicalMedicalDocumentType type;
        String title, filename, media; long size, version = 1; Instant released;
        Row(UUID id, UUID visit, UUID patient, ClinicalMedicalDocumentType type, String title, String filename, String media, long size) {
            this.id = id; this.visit = visit; this.patient = patient; this.type = type; this.title = title; this.filename = filename; this.media = media; this.size = size;
        }
        ClinicalMedicalDocumentJpaEntity snapshot() {
            var result = mock(ClinicalMedicalDocumentJpaEntity.class);
            when(result.getId()).thenReturn(id); when(result.getVisitId()).thenReturn(visit); when(result.getPatientProfileId()).thenReturn(patient);
            when(result.getDocumentType()).thenReturn(type); when(result.getTitle()).thenReturn(title); when(result.getOriginalFilename()).thenReturn(filename);
            when(result.getMediaType()).thenReturn(media); when(result.getByteSize()).thenReturn(size); when(result.getVersion()).thenReturn(version);
            when(result.getReleasedAt()).thenReturn(released); when(result.getCreatedAt()).thenReturn(now); when(result.getUpdatedAt()).thenReturn(now);
            return result;
        }
    }
}
