package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.service.InvoiceService;
import fu.fa26.sep490.g58.otcms_be.clinical.event.ClinicalOrderingRoundCreatedEvent;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Opens an OPEN invoice as soon as a doctor submits a clinical ordering round so patients immediately see
 * pending service items on their portal and can pay online via PayOS or at the counter (UC45, UC55, BR-48).
 */
@Component
public class ClinicalOrderingRoundInvoiceListener {
    private static final Logger log = LoggerFactory.getLogger(ClinicalOrderingRoundInvoiceListener.class);
    private final InvoiceService invoices;
    private final DoctorProfileIdentityQueryService doctors;

    public ClinicalOrderingRoundInvoiceListener(InvoiceService invoices, DoctorProfileIdentityQueryService doctors) {
        this.invoices = invoices;
        this.doctors = doctors;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onClinicalOrderingRoundCreated(ClinicalOrderingRoundCreatedEvent event) {
        if (event == null || event.roundId() == null) return;
        UUID createdByAccountId = event.orderedByDoctorProfileId() == null ? null
                : doctors.findDoctorAccountId(event.orderedByDoctorProfileId()).orElse(null);
        if (createdByAccountId == null) {
            log.warn("Skipping auto-invoice for clinical round {}: doctor account not found for profile {}",
                    event.roundId(), event.orderedByDoctorProfileId());
            return;
        }
        invoices.openForClinicalRound(event.roundId(), createdByAccountId);
    }
}
