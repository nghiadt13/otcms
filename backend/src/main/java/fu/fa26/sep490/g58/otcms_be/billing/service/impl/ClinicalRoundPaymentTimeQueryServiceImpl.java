package fu.fa26.sep490.g58.otcms_be.billing.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.repository.PaymentAttemptRepository;
import fu.fa26.sep490.g58.otcms_be.billing.service.ClinicalRoundPaymentTimeQueryService;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalRoundPaymentTimeQueryServiceImpl implements ClinicalRoundPaymentTimeQueryService {
    private final PaymentAttemptRepository payments;

    public ClinicalRoundPaymentTimeQueryServiceImpl(PaymentAttemptRepository payments) {
        this.payments = payments;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Instant> confirmedPaymentTimes(Collection<UUID> roundIds) {
        if (roundIds.isEmpty()) return Map.of();
        return payments.findConfirmedRoundPaymentTimes(roundIds).stream().collect(Collectors.toMap(
                PaymentAttemptRepository.RoundPaymentTime::getRoundId,
                PaymentAttemptRepository.RoundPaymentTime::getConfirmedAt));
    }
}
