package fu.fa26.sep490.g58.otcms_be.common.audit.impl;

import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.audit.model.AuditLogJpaEntity;
import fu.fa26.sep490.g58.otcms_be.common.audit.repository.AuditLogRepository;
import fu.fa26.sep490.g58.otcms_be.common.security.SensitiveDataRedactor;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AuditLogWriterImpl implements AuditLogWriter {
    private final AuditLogRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AuditLogWriterImpl(AuditLogRepository repository, ObjectMapper mapper, Clock clock) {
        this.repository = repository; this.mapper = mapper; this.clock = clock;
    }

    @Override
    public void record(AuditEntry entry) {
        repository.insert(toEntity(entry));
    }

    @Override
    public boolean recordIfAbsent(AuditEntry entry) {
        if (entry.dedupKey() == null || entry.dedupKey().isBlank()) {
            throw new IllegalArgumentException("An audit deduplication key is required");
        }
        return repository.insertIfAbsent(toEntity(entry)) == 1;
    }

    private AuditLogJpaEntity toEntity(AuditEntry entry) {
        return new AuditLogJpaEntity(entry, entry.occurredAt() == null ? Instant.now(clock) : entry.occurredAt(),
                MDC.get(CorrelationIdFilter.MDC_KEY), json(entry.beforeState()), json(entry.afterState()), json(entry.metadata()));
    }

    private String json(Object value) {
        return value == null ? null : mapper.writeValueAsString(
                SensitiveDataRedactor.redactJson(mapper.convertValue(value, Object.class)));
    }
}
