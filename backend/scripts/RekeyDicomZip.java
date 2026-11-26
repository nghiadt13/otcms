import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;

/** Re-keys one demo DICOM study and writes the identity fields used by OTCMS matching. */
public final class RekeyDicomZip {
    private RekeyDicomZip() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 9) {
            throw new IllegalArgumentException("Expected input, output, PatientID, PatientIssuer, accession number, "
                    + "accession issuer local namespace, optional patient name, optional birth date, optional sex.");
        }

        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        String patientId = required(args[2], "PatientID");
        String patientIssuer = required(args[3], "PatientIssuer");
        String accessionNumber = required(args[4], "AccessionNumber");
        String accessionIssuer = required(args[5], "AccessionIssuerLocalNamespace");
        String patientName = args[6].trim();
        String birthDate = args[7].trim();
        String sex = args[8].trim().toUpperCase(Locale.ROOT);

        if (input.equals(output)) throw new IllegalArgumentException("Input and output ZIP paths must differ.");
        if (!Files.isRegularFile(input)) throw new IllegalArgumentException("Input ZIP does not exist.");
        if (Files.exists(output)) throw new IllegalArgumentException("Output already exists: " + output);
        if (patientId.length() > 64 || patientIssuer.length() > 64 || accessionIssuer.length() > 64) {
            throw new IllegalArgumentException("Patient ID and issuer values must be at most 64 characters.");
        }
        if (accessionNumber.length() > 16) {
            throw new IllegalArgumentException("AccessionNumber must be at most 16 characters (DICOM SH).");
        }
        if (!birthDate.isEmpty() && !birthDate.matches("\\d{8}")) {
            throw new IllegalArgumentException("PatientBirthDate must use YYYYMMDD format.");
        }
        if (!sex.isEmpty() && !Set.of("M", "F", "O").contains(sex)) {
            throw new IllegalArgumentException("PatientSex must be M, F, or O.");
        }

        Path parent = output.getParent();
        if (parent != null) Files.createDirectories(parent);

        String newStudyUid = newUid();
        Map<String, String> seriesUidMap = new HashMap<>();
        Set<String> newSopUids = new HashSet<>();
        Set<String> oldSopUids = new HashSet<>();
        Set<String> entryNames = new HashSet<>();
        int dicomCount = 0;
        String originalStudyUid = null;
        boolean completed = false;

        try (ZipFile source = new ZipFile(input.toFile());
                ZipOutputStream destination = new ZipOutputStream(Files.newOutputStream(output,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            var entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entryNames.add(entry.getName())) {
                    throw new IllegalArgumentException("ZIP contains a duplicate entry name.");
                }
                ZipEntry copied = new ZipEntry(entry.getName());
                if (entry.getTime() >= 0) copied.setTime(entry.getTime());
                destination.putNextEntry(copied);
                if (entry.isDirectory()) {
                    destination.closeEntry();
                    continue;
                }

                if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".dcm")) {
                    try (var in = source.getInputStream(entry)) { in.transferTo(destination); }
                    destination.closeEntry();
                    continue;
                }

                Attributes fileMeta;
                Attributes dataset;
                try (DicomInputStream dicom = new DicomInputStream(source.getInputStream(entry))) {
                    dicom.setIncludeBulkData(DicomInputStream.IncludeBulkData.YES);
                    fileMeta = dicom.readFileMetaInformation();
                    dataset = dicom.readDataset();
                }
                if (fileMeta == null) throw new IllegalArgumentException("DICOM file meta information is missing.");

                String studyUid = required(dataset.getString(Tag.StudyInstanceUID), "StudyInstanceUID");
                String seriesUid = required(dataset.getString(Tag.SeriesInstanceUID), "SeriesInstanceUID");
                String sopUid = required(dataset.getString(Tag.SOPInstanceUID), "SOPInstanceUID");
                if (originalStudyUid == null) originalStudyUid = studyUid;
                else if (!originalStudyUid.equals(studyUid)) {
                    throw new IllegalArgumentException("ZIP contains more than one DICOM study.");
                }
                if (!oldSopUids.add(sopUid)) {
                    throw new IllegalArgumentException("ZIP contains a duplicate SOPInstanceUID.");
                }

                String newSeriesUid = seriesUidMap.computeIfAbsent(seriesUid, ignored -> newUid());
                String newSopUid;
                do { newSopUid = newUid(); } while (!newSopUids.add(newSopUid));

                dataset.setString(Tag.PatientID, VR.LO, patientId);
                dataset.setString(Tag.IssuerOfPatientID, VR.LO, patientIssuer);
                dataset.setString(Tag.AccessionNumber, VR.SH, accessionNumber);
                Attributes accessionIssuerItem = new Attributes();
                accessionIssuerItem.setString(Tag.LocalNamespaceEntityID, VR.UT, accessionIssuer);
                dataset.newSequence(Tag.IssuerOfAccessionNumberSequence, 1).add(accessionIssuerItem);
                dataset.setString(Tag.StudyInstanceUID, VR.UI, newStudyUid);
                dataset.setString(Tag.SeriesInstanceUID, VR.UI, newSeriesUid);
                dataset.setString(Tag.SOPInstanceUID, VR.UI, newSopUid);
                if (!patientName.isEmpty()) dataset.setString(Tag.PatientName, VR.PN, patientName);
                if (!birthDate.isEmpty()) dataset.setString(Tag.PatientBirthDate, VR.DA, birthDate);
                if (!sex.isEmpty()) dataset.setString(Tag.PatientSex, VR.CS, sex);
                fileMeta.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, newSopUid);

                OutputStream sink = new FilterOutputStream(destination) {
                    @Override public void close() throws IOException { flush(); }
                };
                try (DicomOutputStream dicom = new DicomOutputStream(
                        sink, fileMeta.getString(Tag.TransferSyntaxUID))) {
                    dicom.writeDataset(fileMeta, dataset);
                }
                destination.closeEntry();
                dicomCount++;
            }
            if (dicomCount == 0) throw new IllegalArgumentException("ZIP does not contain any .dcm files.");
            completed = true;
        } finally {
            if (!completed) Files.deleteIfExists(output);
        }

        System.out.println("Created re-keyed DICOM ZIP: " + output);
        System.out.println("DICOM instances: " + dicomCount);
        System.out.println("Series UIDs re-keyed: " + seriesUidMap.size());
        System.out.println("Study and SOP instance UIDs re-keyed.");
        System.out.println("DICOM patient and accession identities were written to every instance.");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required.");
        return value.trim();
    }

    private static String newUid() {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return "2.25." + new BigInteger(hex, 16).toString(10);
    }
}
