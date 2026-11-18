package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import java.util.List;
import java.util.UUID;

/** Trusted imaging-only snapshot query. Caller must authorize the visit and patient. */
public interface ResultSheetImagingQueryService {
    List<ImagingFinding> findReleased(UUID patientId, UUID visitId);

    record ImagingFinding(String imagingType, String bodyArea, String findingsText) {
        @Override public String toString() { return "ImagingFinding[redacted]"; }
    }
}
