package fu.fa26.sep490.g58.otcms_be.reporting.service;

import fu.fa26.sep490.g58.otcms_be.reporting.model.ReportResult;

public interface ReportPdfWriter {
    byte[] write(ReportResult report);
}
