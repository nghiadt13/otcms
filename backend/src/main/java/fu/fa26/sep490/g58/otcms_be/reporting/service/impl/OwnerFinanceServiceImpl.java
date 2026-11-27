package fu.fa26.sep490.g58.otcms_be.reporting.service.impl;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportingPeriod;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerAnalyticsDtos.Period;
import fu.fa26.sep490.g58.otcms_be.reporting.model.dto.OwnerFinanceDtos.OwnerFinanceResponse;
import fu.fa26.sep490.g58.otcms_be.reporting.repository.OwnerFinanceRepository;
import fu.fa26.sep490.g58.otcms_be.reporting.service.OwnerFinanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OwnerFinanceServiceImpl implements OwnerFinanceService {
    /** More refunds than this in one period are cut off (newest kept); the summary still counts all of them. */
    static final int MAX_ROWS = 500;
    private final OwnerFinanceRepository repository;

    public OwnerFinanceServiceImpl(OwnerFinanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public OwnerFinanceResponse finance(ReportingPeriod period) {
        return new OwnerFinanceResponse(new Period(period.from().toString(), period.to().toString()), repository.summary(period),
            repository.refundsByPerson(period), repository.refunds(period, MAX_ROWS));
    }
}
