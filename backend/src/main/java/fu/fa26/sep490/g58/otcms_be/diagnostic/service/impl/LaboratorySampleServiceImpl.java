package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.LaboratorySampleJpaEntity;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.LaboratorySampleRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratorySampleService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LaboratorySampleServiceImpl implements LaboratorySampleService {
    private final LaboratorySampleRepository samples;
    public LaboratorySampleServiceImpl(LaboratorySampleRepository samples) { this.samples = samples; }
    @Override @Transactional
    public Sample collect(UUID orderId, UUID accountId, Instant now) {
        var attempts = samples.findAttempts(orderId);
        if (!attempts.isEmpty()) throw new IllegalStateException("Sample already exists");
        var id = UUID.randomUUID();
        var sample = samples.save(new LaboratorySampleJpaEntity(id, orderId, 1, "LAB-" + id,
                accountId, now, accountId, now));
        return view(sample);
    }
    @Override @Transactional
    public Sample ship(UUID orderId, UUID accountId, UUID partnerId, String partner, Instant now) {
        var attempts = samples.findAttempts(orderId);
        if (attempts.isEmpty()) throw new IllegalStateException("Sample is missing");
        var sample = samples.findAttemptForUpdate(attempts.getFirst().getId(), orderId).orElseThrow();
        sample.recordShipment(partnerId, partner, accountId, now);
        return view(sample);
    }
    private Sample view(LaboratorySampleJpaEntity sample) {
        return new Sample(sample.getId(), sample.getSampleCode(), sample.getAttemptNumber(), sample.getCollectedAt(), sample.getPartnerName(), sample.getSentAt());
    }
    @Override @Transactional(readOnly = true)
    public Sample latest(UUID orderId) {
        var attempts = samples.findAttempts(orderId);
        return attempts.isEmpty() ? null : view(attempts.getFirst());
    }
}
