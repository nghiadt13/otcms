package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import java.time.Instant;
import java.util.UUID;

/** Response of {@code POST /api/v1/visits/{visitId}/completion} (plans/261111-0208-visit-flow-r3, section 3, A9). */
public record VisitCompletionResponse(UUID visitId, VisitExamStatus examStatus, Instant closedAt) {
}
