package fu.fa26.sep490.g58.otcms_be.visit.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Raw exam-queue state of one visit, without display names. */
public record ExamQueueEntry(UUID visitId, UUID doctorProfileId, UUID roomId, UUID serviceId, Integer queueNumber,
        VisitExamStatus examStatus, LocalDate clinicDate, Instant queuedAt, Instant startedAt, Instant awaitingAt,
        Instant finishedAt) {
}
