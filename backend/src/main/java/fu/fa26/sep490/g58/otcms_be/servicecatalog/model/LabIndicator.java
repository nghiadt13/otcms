package fu.fa26.sep490.g58.otcms_be.servicecatalog.model;

public record LabIndicator(String name, String unit, ReferenceRange refMale, ReferenceRange refFemale) {
    public record ReferenceRange(double min, double max) { }
}
