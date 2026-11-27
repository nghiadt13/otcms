package fu.fa26.sep490.g58.otcms_be.patient.integration.visit;

import fu.fa26.sep490.g58.otcms_be.patient.repository.PatientMedicalHistoryRepository;
import fu.fa26.sep490.g58.otcms_be.visit.event.VisitCompletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Publishes the doctor-recorded medical history snapshot to the patient portal when a visit completes. */
@Component
public class MedicalHistoryVisitCompletedListener {
    private final PatientMedicalHistoryRepository histories;

    public MedicalHistoryVisitCompletedListener(PatientMedicalHistoryRepository histories) {
        this.histories = histories;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCompleted(VisitCompletedEvent event) {
        if (event != null && event.visitId() != null) {
            histories.publishByVisitId(event.visitId());
        }
    }
}
