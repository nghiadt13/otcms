package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

/** A laboratory value transcribed from the report with the technician's explicit classification. */
public record LaboratoryResultValue(
        String indicator,
        String value,
        String unit,
        String referenceRange,
        String comment,
        Boolean abnormal) { }
