package fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.response;

import java.util.UUID;

public record DiagnosticReportResponse(UUID reportId, String validationStatus, int version) { }
