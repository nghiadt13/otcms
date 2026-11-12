package fu.fa26.sep490.g58.otcms_be.common.audit;

/** Writes audit evidence atomically with the caller's business transaction. */
public interface AuditLogWriter {
    void record(AuditEntry entry);
    boolean recordIfAbsent(AuditEntry entry);
}
