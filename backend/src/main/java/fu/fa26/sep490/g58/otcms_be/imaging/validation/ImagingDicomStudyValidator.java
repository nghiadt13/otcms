package fu.fa26.sep490.g58.otcms_be.imaging.validation;

import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingDicomStudyValidation;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.RequestContext;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.springframework.stereotype.Component;

/** Bounded parser for one DICOM file or one ZIP containing exactly one DICOM study. */
@Component
public class ImagingDicomStudyValidator {
    private static final Set<String> SUPPORTED_MODALITIES = Set.of("CT", "MR", "DX", "CR");
    private static final String ZIP_TYPE = "application/zip";
    private static final String DICOM_TYPE = "application/dicom";
    private static final String MULTIPART_TYPE = "multipart/form-data";

    private final ImagingProperties limits;

    @org.springframework.beans.factory.annotation.Autowired
    public ImagingDicomStudyValidator(ImagingProperties limits) {
        this.limits = limits;
    }

    /** Compatibility constructor for existing validator unit fixtures. */
    public ImagingDicomStudyValidator(ImagingProperties limits,
            fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingIdentityProperties ignored) {
        this(limits);
    }

    public ImagingDicomStudyValidation validate(String contentType, long declaredContentLength,
            InputStream payload, ImagingAssignedWork assignedWork) {
        try (ValidatedDicomStudy validated = validateAndExtract(contentType, declaredContentLength,
                payload, assignedWork)) {
            return validated.summary();
        }
    }

    /**
     * Validates a request and retains only generated temporary files for downstream archive streaming.
     * The caller must close the result after the archive adapter has consumed every file.
     */
    public ValidatedDicomStudy validateAndExtract(String contentType, long declaredContentLength,
            InputStream payload, ImagingAssignedWork assignedWork) {
        List<DicomInstanceMetadata> instances = new ArrayList<>();
        boolean transferred = false;
        try {
            if (limits.getMaxUploadBytes() < 1 || limits.getMaxUploadFiles() < 1) {
                throw new ImagingOperationException(ImagingOperationOutcome.DEPENDENCY_UNAVAILABLE);
            }
            if (declaredContentLength > limits.getMaxUploadBytes()) invalid("BODY_TOO_LARGE");
            if (contentType == null) invalid("UNSUPPORTED_CONTENT_TYPE");
            String normalizedType = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
            BoundedInputStream bounded = new BoundedInputStream(payload, limits.getMaxUploadBytes());
            if (ZIP_TYPE.equals(normalizedType) || "application/x-zip-compressed".equals(normalizedType)) {
                readZip(bounded, instances);
            } else if (DICOM_TYPE.equals(normalizedType)) {
                Path file = copyToTemporaryFile(bounded, limits.getMaxUploadBytes());
                boolean retained = false;
                try {
                    instances.add(readDicom(file));
                    retained = true;
                } finally {
                    if (!retained) Files.deleteIfExists(file);
                }
            } else if (MULTIPART_TYPE.equals(normalizedType)) {
                readMultipart(contentType, declaredContentLength, bounded, instances);
            } else {
                invalid();
            }
            if (instances.isEmpty() || instances.size() > limits.getMaxUploadFiles()) invalid("FILE_COUNT_INVALID");
            ImagingDicomStudyValidation summary = summarize(instances, assignedWork);
            ValidatedDicomStudy validated = new ValidatedDicomStudy(summary,
                    instances.stream().map(instance -> instance.temporaryFile).toList(),
                    instances.stream().map(instance -> new ValidatedDicomStudy.DicomObjectReference(
                            instance.seriesUid, instance.sopUid)).toList());
            transferred = true;
            return validated;
        } catch (ImagingOperationException exception) {
            throw exception;
        } catch (ValidationException exception) {
            throw new ImagingOperationException(ImagingOperationOutcome.FILE_INVALID, exception);
        } catch (IOException | RuntimeException exception) {
            throw new ImagingOperationException(ImagingOperationOutcome.FILE_INVALID,
                    new ValidationException("VALIDATION_RUNTIME_ERROR"));
        } finally {
            if (!transferred) instances.forEach(DicomInstanceMetadata::deleteTemporaryFile);
        }
    }

    private void readMultipart(String contentType, long contentLength, InputStream payload,
            List<DicomInstanceMetadata> instances) throws IOException {
        JakartaServletFileUpload<?, ?> upload = new JakartaServletFileUpload<>();
        upload.setMaxSize(limits.getMaxUploadBytes());
        upload.setMaxFileSize(limits.getMaxUploadBytes());
        upload.setMaxFileCount(limits.getMaxUploadFiles());
        upload.setMaxPartHeaderSize(2 * 1024);
        upload.setHeaderCharset(StandardCharsets.UTF_8);
        RequestContext context = new RequestContext() {
            @Override public String getCharacterEncoding() { return StandardCharsets.UTF_8.name(); }
            @Override public long getContentLength() { return contentLength; }
            @Override public String getContentType() { return contentType; }
            @Override public InputStream getInputStream() { return payload; }
            @Override public boolean isMultipartRelated() { return false; }
        };
        long expandedBytes = 0;
        boolean zipPartSeen = false;
        try {
            FileItemInputIterator iterator = upload.getItemIterator(context);
            while (iterator.hasNext()) {
                FileItemInput item = iterator.next();
                if (item.isFormField() || !"files".equals(item.getFieldName())) {
                    invalid("UNEXPECTED_MULTIPART_FIELD");
                }
                try (InputStream part = item.getInputStream()) {
                    if (isZipContentType(item.getContentType())) {
                        if (zipPartSeen || !instances.isEmpty()) invalid("MIXED_UPLOAD_PART_TYPES");
                        readZip(part, instances);
                        zipPartSeen = true;
                        continue;
                    }
                    if (zipPartSeen) invalid("MIXED_UPLOAD_PART_TYPES");
                    if (instances.size() >= limits.getMaxUploadFiles()) invalid("FILE_COUNT_LIMIT_EXCEEDED");
                    Path file = Files.createTempFile("otcms-imaging-", ".dcm");
                    boolean retained = false;
                    try {
                        long copied = copyZipEntry(part, file, limits.getMaxUploadBytes() - expandedBytes);
                        expandedBytes += copied;
                        instances.add(readDicom(file));
                        retained = true;
                    } finally {
                        if (!retained) Files.deleteIfExists(file);
                    }
                }
            }
        } catch (FileUploadException exception) {
            throw new ValidationException("MULTIPART_INVALID");
        }
    }

    private static boolean isZipContentType(String contentType) {
        if (contentType == null) return false;
        String normalized = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return ZIP_TYPE.equals(normalized) || "application/x-zip-compressed".equals(normalized);
    }

    private void readZip(InputStream payload, List<DicomInstanceMetadata> instances) throws IOException {
        Set<String> entryNames = new HashSet<>();
        long[] expandedBytes = {0};
        try (ZipInputStream zip = new ZipInputStream(payload)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                validateEntryName(entry.getName());
                if (!entryNames.add(entry.getName())) invalid("DUPLICATE_ZIP_ENTRY");
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String extension = Path.of(entry.getName()).getFileName().toString();
                int dot = extension.lastIndexOf('.');
                if (dot >= 0 && !extension.substring(dot).equalsIgnoreCase(".dcm")) {
                    invalid("UNSUPPORTED_ZIP_ENTRY_TYPE");
                }
                if (instances.size() >= limits.getMaxUploadFiles()) invalid("FILE_COUNT_LIMIT_EXCEEDED");
                Path file = Files.createTempFile("otcms-imaging-", ".dcm");
                boolean retained = false;
                try {
                    long copied = copyZipEntry(zip, file, limits.getMaxUploadBytes() - expandedBytes[0]);
                    expandedBytes[0] += copied;
                    instances.add(readDicom(file));
                    retained = true;
                } finally {
                    if (!retained) Files.deleteIfExists(file);
                    zip.closeEntry();
                }
            }
        }
    }

    private static void validateEntryName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")
                || name.indexOf('\\') >= 0 || name.matches("^[A-Za-z]:.*")) invalid("UNSAFE_ZIP_PATH");
        for (String segment : name.split("/")) {
            if (segment.equals("..") || segment.equals(".")) invalid("UNSAFE_ZIP_PATH");
        }
    }

    private static long copyZipEntry(InputStream input, Path destination, long remaining) throws IOException {
        if (remaining < 1) invalid("EXPANDED_SIZE_LIMIT_EXCEEDED");
        long copied = 0;
        byte[] buffer = new byte[16 * 1024];
        try (OutputStream output = Files.newOutputStream(destination)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                copied += read;
                if (copied > remaining) invalid("EXPANDED_SIZE_LIMIT_EXCEEDED");
                output.write(buffer, 0, read);
            }
        }
        if (copied == 0) invalid("EMPTY_DICOM_FILE");
        return copied;
    }

    private Path copyToTemporaryFile(InputStream input, long maxBytes) throws IOException {
        Path file = Files.createTempFile("otcms-imaging-", ".dcm");
        boolean retained = false;
        try {
            long copied = copyZipEntry(input, file, maxBytes);
            if (copied > maxBytes) invalid();
            retained = true;
            return file;
        } finally {
            if (!retained) Files.deleteIfExists(file);
        }
    }

    private DicomInstanceMetadata readDicom(Path file) throws IOException {
        try (DicomInputStream dicom = DicomInputStream.createWithLimitFromFileLength(file.toFile())) {
            dicom.setIncludeBulkData(IncludeBulkData.NO);
            dicom.setAllocateLimit((int) Math.min(limits.getMaxUploadBytes(), 64L * 1024 * 1024));
            Attributes fileMetaInformation = dicom.getFileMetaInformation();
            if (fileMetaInformation == null) invalid("FILE_META_INFORMATION_MISSING");
            Attributes dataset = dicom.readDatasetUntilPixelData();
            String studyUid = required(dataset.getString(Tag.StudyInstanceUID));
            String seriesUid = required(dataset.getString(Tag.SeriesInstanceUID));
            String sopUid = required(dataset.getString(Tag.SOPInstanceUID));
            String modality = required(dataset.getString(Tag.Modality)).toUpperCase(Locale.ROOT);
            if (!SUPPORTED_MODALITIES.contains(modality)) invalid("MODALITY_UNSUPPORTED");
            DicomInstanceMetadata result = new DicomInstanceMetadata(file, studyUid, seriesUid, sopUid, modality);
            return result;
        } catch (IOException exception) {
            throw new ValidationException("DICOM_PARSE_ERROR");
        }
    }

    private ImagingDicomStudyValidation summarize(List<DicomInstanceMetadata> files,
            ImagingAssignedWork assignedWork) {
        if (assignedWork == null || !assignedWork.currentlyAssigned()) invalid("ASSIGNED_WORK_REQUIRED");
        DicomInstanceMetadata first = files.getFirst();
        Set<String> seriesUids = new HashSet<>();
        Set<String> sopUids = new HashSet<>();
        List<String> evidence = new ArrayList<>();
        for (DicomInstanceMetadata file : files) {
            if (!first.studyUid.equals(file.studyUid)) invalid("MULTIPLE_STUDIES_IN_ARCHIVE");
            if (!first.modality.equals(file.modality)) invalid("MIXED_MODALITY_IN_STUDY");
            if (!sopUids.add(file.sopUid)) invalid("DUPLICATE_INSTANCE_UID");
            seriesUids.add(file.seriesUid);
        }
        // The authenticated assigned-work context is authoritative for linking this upload.
        // DICOM patient/accession tags do not block or establish that link.
        evidence.add("REQUEST_ATTEMPT_CONTEXT");
        return result(ImagingVerificationStatus.REQUEST_CONTEXT_MATCH, first, seriesUids.size(), files.size(), evidence);
    }

    private static ImagingDicomStudyValidation result(ImagingVerificationStatus status,
            DicomInstanceMetadata first, int seriesCount, int instanceCount, List<String> evidence) {
        if (evidence.isEmpty()) evidence.add("IDENTITY_EVIDENCE_COMPLETE");
        return new ImagingDicomStudyValidation(status, first.studyUid, first.modality,
                seriesCount, instanceCount, evidence);
    }

    private static String required(String value) {
        String clean = clean(value);
        if (clean == null) invalid();
        return clean;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private static void invalid() {
        invalid("INVALID_ARCHIVE_OR_DICOM");
    }

    private static void invalid(String safeCode) {
        throw new ValidationException(safeCode);
    }

    private static final class ValidationException extends RuntimeException {
        private ValidationException(String safeCode) { super(safeCode); }
    }

    private static final class DicomInstanceMetadata {
        private final Path temporaryFile;
        private final String studyUid;
        private final String seriesUid;
        private final String sopUid;
        private final String modality;

        private DicomInstanceMetadata(Path temporaryFile, String studyUid, String seriesUid, String sopUid,
                String modality) {
            this.temporaryFile = temporaryFile; this.studyUid = studyUid; this.seriesUid = seriesUid;
            this.sopUid = sopUid; this.modality = modality;
        }

        private void deleteTemporaryFile() {
            try { Files.deleteIfExists(temporaryFile); } catch (IOException ignored) { }
        }
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private final long maxBytes;
        private long read;

        private BoundedInputStream(InputStream input, long maxBytes) {
            super(input);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1 && ++read > maxBytes) invalid();
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = super.read(bytes, offset, length);
            if (count > 0 && (read += count) > maxBytes) invalid();
            return count;
        }
    }
}
