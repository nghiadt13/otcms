package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.*;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceResponse(UUID id, String name, String description, BigDecimal price, int durationMinutes,
        int bufferMinutes, boolean onlineBookable, boolean active) { }
