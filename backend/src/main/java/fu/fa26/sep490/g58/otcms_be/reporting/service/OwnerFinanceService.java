package fu.fa26.sep490.g58.otcms_be.reporting.service;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerFinanceDtos.OwnerFinanceResponse;

/** Finance overview for the clinic Owner: money collected and refunded, and who confirmed each refund. Read-only. */
public interface OwnerFinanceService {
    OwnerFinanceResponse finance(ReportingPeriod period);
}
