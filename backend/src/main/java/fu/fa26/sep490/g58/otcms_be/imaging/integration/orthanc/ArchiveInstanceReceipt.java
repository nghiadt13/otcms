package fu.fa26.sep490.g58.otcms_be.imaging.integration.orthanc;

/** Opaque Orthanc references returned after a confirmed successful instance store. */
public record ArchiveInstanceReceipt(String archiveInstanceRef, String archiveSeriesRef,
        String archiveStudyRef) {
    public ArchiveInstanceReceipt {
        archiveInstanceRef = requireOpaqueReference(archiveInstanceRef);
        archiveSeriesRef = requireOpaqueReference(archiveSeriesRef);
        archiveStudyRef = requireOpaqueReference(archiveStudyRef);
    }

    private static String requireOpaqueReference(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Archive response reference is invalid");
        }
        return value;
    }
}
