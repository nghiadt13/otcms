package fu.fa26.sep490.g58.otcms_be.imaging.model;

/** DICOM identity assigned by OTCMS to the currently authorized imaging request. */
public record ImagingDicomIdentity(String patientId, String patientIssuer,
        String accessionNumber, String accessionIssuerLocalNamespace) { }
