package com.otcms.imaging.application.port.out;

/**
 * Boundary for DICOM archive operations. Imaging use cases depend on this port,
 * while the Orthanc adapter will implement it when the imaging workflow is built.
 */
public interface ImagingArchivePort {
    boolean containsStudy(String studyInstanceUid);
}
