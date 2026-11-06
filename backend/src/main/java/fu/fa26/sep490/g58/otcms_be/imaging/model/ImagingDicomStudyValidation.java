package fu.fa26.sep490.g58.otcms_be.imaging.model;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import java.util.List;
import java.util.Objects;

/** Safe validation projection; contains no PatientID, accession value, issuer, or free-text DICOM tag. */
public record ImagingDicomStudyValidation(ImagingVerificationStatus verificationStatus,
        String studyInstanceUid, String modality, int seriesCount, int instanceCount,
        List<String> evidenceCodes) {
    public ImagingDicomStudyValidation {
        Objects.requireNonNull(verificationStatus, "verificationStatus");
        Objects.requireNonNull(studyInstanceUid, "studyInstanceUid");
        Objects.requireNonNull(modality, "modality");
        evidenceCodes = List.copyOf(evidenceCodes);
        if (seriesCount < 1 || instanceCount < 1) {
            throw new IllegalArgumentException("A validated study must contain series and instances");
        }
    }
}
