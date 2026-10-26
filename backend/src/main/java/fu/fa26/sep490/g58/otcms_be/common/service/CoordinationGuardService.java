package fu.fa26.sep490.g58.otcms_be.common.service;

/** Acquires the shared transaction guard before booking-affecting reads and writes. */
public interface CoordinationGuardService {
    /**
     * Requires an active transaction, applies its bounded PostgreSQL timeouts,
     * and locks the shared coordination row.
     */
    void acquire();
}
