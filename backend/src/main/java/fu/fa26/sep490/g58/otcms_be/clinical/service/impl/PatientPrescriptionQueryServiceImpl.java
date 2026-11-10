package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalPrescriptionItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalPrescriptionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionService.PrescriptionLine;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import fu.fa26.sep490.g58.otcms_be.content.service.ClinicContentService;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PatientPrescriptionQueryServiceImpl implements PatientPrescriptionQueryService {
    private final PatientProfileAccessService access;
    private final PatientProfileDirectory profiles;
    private final DoctorProfileIdentityQueryService doctors;
    private final ClinicalPrescriptionRepository prescriptions;
    private final ClinicalPrescriptionItemRepository items;
    private final VisitClinicalContextPort visits;
    private final ClinicalExaminationHistoryQueryService examinations;
    private final ClinicContentService clinic;

    public PatientPrescriptionQueryServiceImpl(PatientProfileAccessService access, PatientProfileDirectory profiles,
            DoctorProfileIdentityQueryService doctors, ClinicalPrescriptionRepository prescriptions,
            ClinicalPrescriptionItemRepository items, VisitClinicalContextPort visits,
            ClinicalExaminationHistoryQueryService examinations, ClinicContentService clinic) {
        this.access = access; this.profiles = profiles; this.doctors = doctors; this.prescriptions = prescriptions;
        this.items = items; this.visits = visits; this.examinations = examinations; this.clinic = clinic;
    }

    @Override
    public PrescriptionPage list(AuthenticatedAccount actor, UUID patientId, int page, int size) {
        // New account without its own profile yet: an empty list, not an error.
        if (patientId == null && actor != null && actor.accountId() != null && actor.accountRole() == AccountRole.PATIENT
                && access.accessibleProfileIds(actor.accountId()).isEmpty()) {
            return new PrescriptionPage(List.of(), page, size, 0, 0);
        }
        var allowed = scope(actor);
        if (patientId != null && !allowed.contains(patientId)) throw error("MSG-40");
        if (page < 0 || size < 1 || size > 100) throw error("MSG-39");
        var selected = patientId == null ? allowed : Set.of(patientId);
        var headers = prescriptions.findByPatientProfileIdInAndStatus(selected, ClinicalPrescriptionStatus.ISSUED,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("issuedAt"), Sort.Order.desc("id"))));
        if (headers.isEmpty()) return new PrescriptionPage(List.of(), page, size, headers.getTotalElements(), headers.getTotalPages());
        var patients = profiles.findProfiles(headers.stream().map(ClinicalPrescriptionJpaEntity::getPatientProfileId).distinct().toList())
                .stream().collect(Collectors.toMap(PatientProfileDirectory.ProfileRecord::id, p -> p));
        var names = doctors.findDoctorNames(headers.stream().map(ClinicalPrescriptionJpaEntity::getDoctorProfileId).distinct().toList());
        var lines = items.findByPrescriptionIdInOrderByPrescriptionIdAscLineNumberAsc(headers.stream().map(ClinicalPrescriptionJpaEntity::getId).toList())
                .stream().collect(Collectors.groupingBy(ClinicalPrescriptionItemJpaEntity::getPrescriptionId, Collectors.counting()));
        var result = headers.stream().map(p -> {
            // Defensive owner invariants: never project a repository row outside the authorized selection.
            if (!selected.contains(p.getPatientProfileId()) || p.getStatus() != ClinicalPrescriptionStatus.ISSUED) throw error("DEPENDENCY_UNAVAILABLE");
            return new Summary(p.getId(), p.getIssuedAt(), lines.getOrDefault(p.getId(), 0L).intValue(),
                    names.get(p.getDoctorProfileId()), patient(actor, patients.get(p.getPatientProfileId())));
        }).toList();
        return new PrescriptionPage(result, page, size, headers.getTotalElements(), headers.getTotalPages());
    }

    @Override
    public Detail detail(AuthenticatedAccount actor, UUID prescriptionId) {
        var allowed = scope(actor);
        if (prescriptionId == null) throw error("MSG-39");
        var prescription = prescriptions.findById(prescriptionId).orElseThrow(() -> error("MSG-41"));
        if (!allowed.contains(prescription.getPatientProfileId())) throw error("MSG-40");
        if (prescription.getStatus() != ClinicalPrescriptionStatus.ISSUED) throw error("MSG-41");
        var visit = visits.getVisitContext(prescription.getVisitId());
        if (visit == null || !prescription.getVisitId().equals(visit.visitId())
                || !prescription.getPatientProfileId().equals(visit.patientId())) throw error("MSG-41");
        var profile = profiles.findProfile(prescription.getPatientProfileId()).orElseThrow(() -> error("MSG-41"));
        var completed = examinations.findCompletedByVisitIds(List.of(prescription.getVisitId())).get(prescription.getVisitId());
        String diagnosis = completed == null ? null : completed.examination().diagnoses().stream()
                .map(d -> d.code()).collect(Collectors.joining(", "));
        var contact = clinic.publicContact();
        var lines = items.findByPrescriptionIdOrderByLineNumberAsc(prescriptionId).stream()
                .map(i -> new PrescriptionLine(i.getLineNumber(), i.getMedicine(), i.getDose(), i.getFrequency(), i.getUsage())).toList();
        return new Detail(prescriptionId, prescription.getVisitId(), prescription.getIssuedAt(),
                doctors.findDoctorNames(List.of(prescription.getDoctorProfileId())).get(prescription.getDoctorProfileId()),
                contact == null ? null : contact.clinicName(), patient(actor, profile), diagnosis, lines);
    }

    private Set<UUID> scope(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.PATIENT) throw error("MSG-40");
        var allowed = access.accessibleProfileIds(actor.accountId());
        if (allowed.isEmpty()) throw error("MSG-40");
        return allowed;
    }

    private Patient patient(AuthenticatedAccount actor, PatientProfileDirectory.ProfileRecord profile) {
        if (profile == null) throw error("DEPENDENCY_UNAVAILABLE");
        return new Patient(profile.id(), profile.fullName(), profile.dateOfBirth(),
                actor.accountId().equals(profile.accountId()) ? "SELF" : "DEPENDENT");
    }
    private static ClinicalOperationException error(String code) { return new ClinicalOperationException(code); }
}
