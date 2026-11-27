package fu.fa26.sep490.g58.otcms_be.reporting.repository;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerFinanceDtos.RefundByPerson;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerFinanceDtos.RefundRow;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerFinanceDtos.Summary;
import java.util.List;

/** Read-only sources of the Owner's finance page. */
public interface OwnerFinanceRepository {
    /** Money collected in the period (confirmed payments), refunds completed in it, and refunds still pending now. */
    Summary summary(ReportingPeriod period);

    /** Refunds completed in the period, grouped by the person who confirmed them, largest first. */
    List<RefundByPerson> refundsByPerson(ReportingPeriod period);

    /** Pending refunds (longest waiting first), then refunds completed in the period (newest first); at most {@code limit}. */
    List<RefundRow> refunds(ReportingPeriod period, int limit);
}
