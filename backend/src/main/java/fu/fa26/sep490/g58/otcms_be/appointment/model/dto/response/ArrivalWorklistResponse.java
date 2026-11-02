package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response;

import java.time.LocalDate;
import java.util.List;

public record ArrivalWorklistResponse(LocalDate date, int page, int size, boolean hasMore,
        List<ArrivalResponse> items) { }
