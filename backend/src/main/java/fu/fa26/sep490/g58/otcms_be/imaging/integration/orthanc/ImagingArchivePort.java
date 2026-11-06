package fu.fa26.sep490.g58.otcms_be.imaging.integration.orthanc;

import java.nio.file.Path;

/** Private Orthanc boundary. Callers pass one validated DICOM instance at a time. */
public interface ImagingArchivePort {
    ArchiveInstanceReceipt storeInstance(Path dicomFile);
}
