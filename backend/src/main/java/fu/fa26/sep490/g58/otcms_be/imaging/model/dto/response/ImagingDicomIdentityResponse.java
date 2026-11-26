package fu.fa26.sep490.g58.otcms_be.imaging.model.dto.response;

/** DICOM tags supplied only to the technician currently assigned to the imaging request. */
public record ImagingDicomIdentityResponse(String patientId, String patientIssuer,
        String accessionNumber, String accessionIssuerLocalNamespace) { }
