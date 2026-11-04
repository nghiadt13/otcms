package fu.fa26.sep490.g58.otcms_be.imaging.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingIdentityProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingDicomStudyValidation;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingVerificationStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;

class ImagingDicomStudyValidatorTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ATTEMPT = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID VISIT = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID OTHER_PATIENT = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final String STUDY_UID = "1.2.826.0.1.3680043.10.543.1";
    private static final String SERIES_UID = "1.2.826.0.1.3680043.10.543.1.1";
    private static final ImagingAssignedWork WORK = new ImagingAssignedWork(
            ACTOR, REQUEST, ATTEMPT, VISIT, PATIENT, "assignment-v1", true);

    @Test
    void validatesSingleDicomFileAndConfirmsBothOwnerMappings() throws Exception {
        var result = validator(identityMappings(PATIENT, REQUEST)).validate("application/dicom", -1,
                new ByteArrayInputStream(dicom("CT", STUDY_UID, SERIES_UID, sopUid(1), true)), WORK);

        assertEquals(ImagingVerificationStatus.CONFIRMED_MATCH, result.verificationStatus());
        assertEquals("CT", result.modality());
        assertEquals(1, result.seriesCount());
        assertEquals(1, result.instanceCount());
    }

    @Test
    void validatesOneStudyZipWithMultipleInstances() throws Exception {
        byte[] archive = zip(List.of(
                new Entry("slice-1.dcm", dicom("MR", STUDY_UID, SERIES_UID, sopUid(1), false)),
                new Entry("slice-2.dcm", dicom("MR", STUDY_UID, SERIES_UID, sopUid(2), false))));

        ImagingDicomStudyValidation result = validator(new ImagingIdentityProperties()).validate(
                "application/zip", archive.length, new ByteArrayInputStream(archive), WORK);

        assertEquals(ImagingVerificationStatus.UNKNOWN, result.verificationStatus());
        assertEquals("MR", result.modality());
        assertEquals(2, result.instanceCount());
        assertEquals(List.of("PATIENT_ISSUER_MISSING", "ACCESSION_ISSUER_MISSING"), result.evidenceCodes().stream()
                .filter(code -> code.contains("ISSUER_MISSING")).toList());
    }

    @Test
    void validatesStreamingMultipartWithMultipleDicomFiles() throws Exception {
        String boundary = "otcms-test-boundary";
        byte[] body = multipart(boundary, List.of(
                new Entry("slice-1.dcm", dicom("CT", STUDY_UID, SERIES_UID, sopUid(10), false)),
                new Entry("slice-2.dcm", dicom("CT", STUDY_UID, SERIES_UID, sopUid(11), false))));

        ImagingDicomStudyValidation result = validator(new ImagingIdentityProperties()).validate(
                "multipart/form-data; boundary=" + boundary, body.length, new ByteArrayInputStream(body), WORK);

        assertEquals(ImagingVerificationStatus.UNKNOWN, result.verificationStatus());
        assertEquals(2, result.instanceCount());
    }

    @Test
    void quarantinesIdentityMappingThatPointsToAnotherOwner() throws Exception {
        var result = validator(identityMappings(OTHER_PATIENT, REQUEST)).validate("application/dicom", -1,
                new ByteArrayInputStream(dicom("DX", STUDY_UID, SERIES_UID, sopUid(3), true)), WORK);

        assertEquals(ImagingVerificationStatus.MISMATCH, result.verificationStatus());
        assertEquals(List.of("PATIENT_OWNER_MISMATCH"), result.evidenceCodes());
    }

    @Test
    void rejectsMultipleStudiesAndRepeatedInstanceUids() throws Exception {
        ImagingDicomStudyValidator validator = validator(new ImagingIdentityProperties());
        byte[] multipleStudies = zip(List.of(
                new Entry("slice-1.dcm", dicom("CT", STUDY_UID, SERIES_UID, sopUid(4), true)),
                new Entry("slice-2.dcm", dicom("CT", "1.2.826.0.1.3680043.10.543.2",
                        "1.2.826.0.1.3680043.10.543.2.1", sopUid(5), true))));
        byte[] repeatedSop = zip(List.of(
                new Entry("slice-1.dcm", dicom("CT", STUDY_UID, SERIES_UID, sopUid(6), true)),
                new Entry("slice-2.dcm", dicom("CT", STUDY_UID, SERIES_UID, sopUid(6), true))));

        assertInvalid(validator, multipleStudies, "application/zip", "MULTIPLE_STUDIES_IN_ARCHIVE");
        assertInvalid(validator, repeatedSop, "application/zip", "DUPLICATE_INSTANCE_UID");
    }

    @Test
    void rejectsUnsupportedModalityMalformedDicomAndZipTraversal() throws Exception {
        ImagingDicomStudyValidator validator = validator(new ImagingIdentityProperties());
        byte[] unsupported = dicom("OT", STUDY_UID, SERIES_UID, sopUid(7), true);
        byte[] traversal = zip(List.of(new Entry("../escape.dcm", new byte[] {1, 2, 3})));

        assertInvalid(validator, unsupported, "application/dicom", "MODALITY_UNSUPPORTED");
        assertInvalid(validator, new byte[] {1, 2, 3}, "application/dicom", "DICOM_PARSE_ERROR");
        assertInvalid(validator, traversal, "application/zip", "UNSAFE_ZIP_PATH");
    }

    @Test
    void rejectsDeclaredOversizePayloadBeforeReadingIt() {
        ImagingProperties limits = new ImagingProperties();
        limits.setMaxUploadBytes(64);
        limits.setMaxUploadFiles(2);
        ImagingDicomStudyValidator validator = new ImagingDicomStudyValidator(limits,
                new ImagingIdentityProperties());
        ImagingOperationException error = assertThrows(ImagingOperationException.class,
                () -> validator.validate("application/zip", 65, new ByteArrayInputStream(new byte[0]), WORK));

        assertEquals(ImagingOperationOutcome.FILE_INVALID, error.outcome());
        assertEquals("BODY_TOO_LARGE", error.getCause().getMessage());
    }

    private static void assertInvalid(ImagingDicomStudyValidator validator, byte[] payload,
            String contentType, String expectedCode) {
        ImagingOperationException error = assertThrows(ImagingOperationException.class,
                () -> validator.validate(contentType, payload.length, new ByteArrayInputStream(payload), WORK));
        assertEquals(ImagingOperationOutcome.FILE_INVALID, error.outcome());
        assertEquals(expectedCode, error.getCause().getMessage());
    }

    private static ImagingDicomStudyValidator validator(ImagingIdentityProperties mappings) {
        ImagingProperties limits = new ImagingProperties();
        limits.setMaxUploadBytes(2 * 1024 * 1024);
        limits.setMaxUploadFiles(40);
        return new ImagingDicomStudyValidator(limits, mappings);
    }

    private static ImagingIdentityProperties identityMappings(UUID patient, UUID request) {
        ImagingIdentityProperties mappings = new ImagingIdentityProperties();
        mappings.setPatientOwnerByIssuer(Map.of("issuer-a", Map.of("patient-a", patient)));
        mappings.setRequestByAccessionIssuer(Map.of("LOCAL=clinic-a", Map.of("request-a", request)));
        return mappings;
    }

    private static byte[] dicom(String modality, String studyUid, String seriesUid, String sopUid,
            boolean includeIssuerEvidence) throws Exception {
        Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, sopClassFor(modality));
        dataset.setString(Tag.SOPInstanceUID, VR.UI, sopUid);
        dataset.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
        dataset.setString(Tag.Modality, VR.CS, modality);
        dataset.setString(Tag.PatientID, VR.LO, "patient-a");
        dataset.setString(Tag.AccessionNumber, VR.SH, "request-a");
        if (includeIssuerEvidence) {
            dataset.setString(Tag.IssuerOfPatientID, VR.LO, "issuer-a");
            Attributes accessionIssuer = new Attributes();
            accessionIssuer.setString(Tag.LocalNamespaceEntityID, VR.UT, "clinic-a");
            dataset.newSequence(Tag.IssuerOfAccessionNumberSequence, 1).add(accessionIssuer);
        }
        Attributes fileMeta = dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DicomOutputStream dicom = new DicomOutputStream(output, UID.ExplicitVRLittleEndian)) {
            dicom.writeDataset(fileMeta, dataset);
        }
        return output.toByteArray();
    }

    private static String sopClassFor(String modality) {
        return switch (modality) {
            case "CT" -> UID.CTImageStorage;
            case "MR" -> UID.MRImageStorage;
            case "DX" -> UID.DigitalXRayImageStorageForPresentation;
            default -> UID.SecondaryCaptureImageStorage;
        };
    }

    private static String sopUid(int value) {
        return "1.2.826.0.1.3680043.10.543.100." + value;
    }

    private static byte[] zip(List<Entry> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Entry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] multipart(String boundary, List<Entry> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (Entry entry : entries) {
            output.write(("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"files\"; filename=\"" + entry.name() + "\"\r\n"
                    + "Content-Type: application/dicom\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.write(entry.content());
            output.write("\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
        output.write(("--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return output.toByteArray();
    }

    private record Entry(String name, byte[] content) { }
}
