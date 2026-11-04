package fu.fa26.sep490.g58.otcms_be.imaging.validation;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingDicomStudyValidation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Temporary validated DICOM objects for the archive adapter; the caller must close this resource. */
public final class ValidatedDicomStudy implements AutoCloseable {
    private final ImagingDicomStudyValidation summary;
    private final List<Path> instanceFiles;
    private final List<DicomObjectReference> objects;
    private final AtomicBoolean closed = new AtomicBoolean();

    ValidatedDicomStudy(ImagingDicomStudyValidation summary, List<Path> instanceFiles,
            List<DicomObjectReference> objects) {
        this.summary = summary;
        this.instanceFiles = List.copyOf(instanceFiles);
        this.objects = List.copyOf(objects);
    }

    public ImagingDicomStudyValidation summary() {
        return summary;
    }

    /** Generated temporary paths only; never use the original client filename. */
    public List<Path> instanceFiles() {
        if (closed.get()) throw new IllegalStateException("Validated DICOM study is closed");
        return instanceFiles;
    }

    public List<DicomObjectReference> objects() {
        if (closed.get()) throw new IllegalStateException("Validated DICOM study is closed");
        return objects;
    }

    public record DicomObjectReference(String seriesInstanceUid, String sopInstanceUid) { }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        boolean failed = false;
        for (Path file : instanceFiles) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                failed = true;
            }
        }
        if (failed) {
            throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE,
                    new IllegalStateException("TEMPORARY_DICOM_CLEANUP_FAILED"));
        }
    }
}
