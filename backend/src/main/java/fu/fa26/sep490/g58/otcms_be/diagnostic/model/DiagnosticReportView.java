package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import java.util.UUID;

public record DiagnosticReportView(UUID reportId, String validationStatus, int version) { }
