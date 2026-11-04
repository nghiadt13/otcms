package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ClinicalPrescriptionServiceImpl implements ClinicalPrescriptionService {
    private final VisitClinicalContextPort visits;
    private final ClinicalPrescriptionRepository prescriptions;
    private final ClinicalPrescriptionItemRepository items;
    private final Clock clock;

    public ClinicalPrescriptionServiceImpl(VisitClinicalContextPort visits, ClinicalPrescriptionRepository prescriptions,
            ClinicalPrescriptionItemRepository items, Clock clock) {
        this.visits = visits; this.prescriptions = prescriptions; this.items = items; this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PrescriptionView getPrescription(UUID visitId, UUID doctorProfileId) {
        var context = requireCare(visitId, doctorProfileId);
        var prescription = requirePrescription(visitId);
        requireLinkage(prescription, context);
        return view(prescription);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PrescriptionView saveDraft(DraftCommand command) {
        if (command == null) throw error("MSG-39");
        var context = requireCare(command.visitId(), command.doctorProfileId());
        requireVersion(command.version());
        var lines = validateItems(command.items());
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID id;
        if (command.version() == 0) {
            id = UUID.randomUUID();
            if (prescriptions.insertDraft(id, command.visitId(), context.patientId(), command.doctorProfileId(), now) != 1) throw error("MSG-42");
        } else {
            var prescription = requirePrescription(command.visitId());
            requireLinkage(prescription, context);
            if (prescription.getStatus() != ClinicalPrescriptionStatus.DRAFT || prescription.getVersion() != command.version()) throw error("MSG-42");
            id = prescription.getId();
            if (prescriptions.advanceDraftVersion(id, command.version(), command.doctorProfileId(), now) != 1) throw error("MSG-42");
            // No item mutation until the expected-version header write wins its database row lock.
            items.deleteItems(id);
        }
        var entities = new ArrayList<ClinicalPrescriptionItemJpaEntity>();
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            entities.add(new ClinicalPrescriptionItemJpaEntity(id, index + 1, line.medicine(), line.dose(), line.frequency(), line.usage()));
        }
        items.saveAllAndFlush(entities);
        return view(requirePrescription(command.visitId()));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PrescriptionView issue(IssuanceCommand command) {
        if (command == null) throw error("MSG-39");
        var context = requireCare(command.visitId(), command.doctorProfileId());
        requireVersion(command.version());
        if (command.version() == 0 || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 128) throw error("MSG-39");
        String key = command.idempotencyKey().trim();
        var prescription = requirePrescription(command.visitId());
        requireLinkage(prescription, context);
        if (prescription.getStatus() == ClinicalPrescriptionStatus.ISSUED) return replay(prescription, key, command.version(), command.doctorProfileId());
        if (prescription.getVersion() != command.version()) throw error("MSG-42");
        validateItems(items.findByPrescriptionIdOrderByLineNumberAsc(prescription.getId()).stream()
                .map(line -> new MedicineItem(line.getMedicine(), line.getDose(), line.getFrequency(), line.getUsage())).toList());
        if (prescriptions.issueDraft(prescription.getId(), command.version(), key, command.doctorProfileId(),
                clock.instant().truncatedTo(ChronoUnit.MICROS)) == 1) return view(requirePrescription(command.visitId()));
        // A conditional update is not a failed/rollback-only transaction. Reload after a competing commit.
        var winner = requirePrescription(command.visitId());
        requireLinkage(winner, context);
        if (winner.getStatus() == ClinicalPrescriptionStatus.ISSUED) return replay(winner, key, command.version(), command.doctorProfileId());
        throw error("MSG-42");
    }

    private PrescriptionView replay(ClinicalPrescriptionJpaEntity prescription, String key, long version, UUID doctorId) {
        if (!doctorId.equals(prescription.getDoctorProfileId())) throw error("MSG-42");
        if (!key.equals(prescription.getIssuanceKey())) throw error("MSG-42");
        if (!Long.valueOf(version).equals(prescription.getIssuanceExpectedVersion())) throw error("IDEMPOTENCY_CONFLICT");
        return view(prescription);
    }

    private VisitClinicalContextPort.ClinicalVisitContext requireCare(UUID visitId, UUID doctorId) {
        if (visitId == null) throw error("MSG-39");
        if (doctorId == null) throw error("MSG-40");
        var context = visits.getVisitContext(visitId);
        if (context == null) throw error("MSG-41");
        if (!doctorId.equals(context.responsibleDoctorId())) throw error("MSG-40");
        if (!context.clinicalWorkAllowed()) throw error("MSG-42");
        if (!visitId.equals(context.visitId()) || context.patientId() == null) throw error("MSG-39");
        return context;
    }

    private void requireLinkage(ClinicalPrescriptionJpaEntity prescription,
            VisitClinicalContextPort.ClinicalVisitContext context) {
        if (!context.visitId().equals(prescription.getVisitId()) || !context.patientId().equals(prescription.getPatientProfileId())) throw error("MSG-41");
    }
    private ClinicalPrescriptionJpaEntity requirePrescription(UUID visitId) {
        return prescriptions.findByVisitId(visitId).orElseThrow(() -> error("MSG-41"));
    }
    private PrescriptionView view(ClinicalPrescriptionJpaEntity prescription) {
        return new PrescriptionView(prescription.getId(), prescription.getVisitId(), prescription.getPatientProfileId(),
                prescription.getDoctorProfileId(), prescription.getStatus(), prescription.getIssuedAt(), prescription.getVersion(),
                items.findByPrescriptionIdOrderByLineNumberAsc(prescription.getId()).stream().map(line -> new PrescriptionLine(
                        line.getLineNumber(), line.getMedicine(), line.getDose(), line.getFrequency(), line.getUsage())).toList());
    }
    private static void requireVersion(Long version) {
        if (version == null || version < 0 || version == Long.MAX_VALUE) throw error("MSG-39");
    }
    private static List<MedicineItem> validateItems(List<MedicineItem> lines) {
        if (lines == null || lines.isEmpty()) throw error("MSG-39");
        return lines.stream().map(line -> {
            if (line == null) throw error("MSG-39");
            return new MedicineItem(text(line.medicine(), 255), text(line.dose(), 255), text(line.frequency(), 255), text(line.usage(), 2000));
        }).toList();
    }
    private static String text(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) throw error("MSG-39");
        return value.trim();
    }
    private static ClinicalOperationException error(String code) { return new ClinicalOperationException(code); }
}
