package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReportValidationStatus;

/** Parses and scans a bounded report before it can be released. */
public interface ReportContentValidator {
    ReportValidationStatus validate(byte[] content);
}
