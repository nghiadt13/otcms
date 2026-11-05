package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalPrescriptionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionHistoryQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalPrescriptionService.PrescriptionLine;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ClinicalPrescriptionHistoryQueryServiceImpl implements ClinicalPrescriptionHistoryQueryService {
    private final ClinicalPrescriptionRepository prescriptions;
    private final ClinicalPrescriptionItemRepository items;
    public ClinicalPrescriptionHistoryQueryServiceImpl(ClinicalPrescriptionRepository prescriptions, ClinicalPrescriptionItemRepository items) {
        this.prescriptions = prescriptions; this.items = items;
    }
    @Override public List<IssuedPrescriptionSummary> findIssuedByPatient(UUID patientProfileId) {
        if (patientProfileId == null) throw new ClinicalOperationException("MSG-39");
        return prescriptions.findByPatientProfileIdAndStatusOrderByIssuedAtDescIdDesc(patientProfileId, ClinicalPrescriptionStatus.ISSUED)
                .stream().map(this::summary).toList();
    }
    @Override public List<IssuedPrescriptionSummary> findIssuedByVisit(UUID visitId) {
        if (visitId == null) throw new ClinicalOperationException("MSG-39");
        return prescriptions.findByVisitIdAndStatusOrderByIssuedAtDescIdDesc(visitId, ClinicalPrescriptionStatus.ISSUED)
                .stream().map(this::summary).toList();
    }
    private IssuedPrescriptionSummary summary(ClinicalPrescriptionJpaEntity prescription) {
        return new IssuedPrescriptionSummary(prescription.getId(), prescription.getVisitId(), prescription.getPatientProfileId(),
                prescription.getDoctorProfileId(), prescription.getIssuedAt(), prescription.getVersion(),
                items.findByPrescriptionIdOrderByLineNumberAsc(prescription.getId()).stream().map(line -> new PrescriptionLine(
                        line.getLineNumber(), line.getMedicine(), line.getDose(), line.getFrequency(), line.getUsage())).toList());
    }
}
