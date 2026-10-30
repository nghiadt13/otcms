package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.time.LocalDate;
import java.util.List;

public record ReceptionQueueResponse(LocalDate date, int page, int size, boolean hasMore,
        List<ReceptionQueueItemResponse> items) { }
