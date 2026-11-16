import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.dcm4che3.data.*;
import org.dcm4che3.io.*;

/** Creates a demo-only archive with explicit test identity; never use on clinical studies. */
public class PrepareDemoDicom {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected input and new output ZIP paths");
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Output already exists");
        int count = 0;
        try (ZipFile input = new ZipFile(args[0])) {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW))) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (!entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".dcm")) {
                    throw new IllegalArgumentException("Unexpected non-DICOM entry");
                }
                Attributes fmi;
                Attributes dataset;
                try (var stream = new DicomInputStream(input.getInputStream(entry))) {
                    stream.setIncludeBulkData(DicomInputStream.IncludeBulkData.YES);
                    fmi = stream.readFileMetaInformation();
                    dataset = stream.readDataset();
                }
                if (fmi == null) throw new IllegalArgumentException("Missing file meta information");
                dataset.setString(Tag.PatientID, VR.LO, "OTCMS-DEMO-PATIENT");
                dataset.setString(Tag.IssuerOfPatientID, VR.LO, "OTCMS-DEMO");
                dataset.setString(Tag.AccessionNumber, VR.SH, "OTCMS-DEMO-ORDER");
                var issuer = new Attributes();
                issuer.setString(Tag.LocalNamespaceEntityID, VR.UT, "OTCMS-DEMO");
                dataset.newSequence(Tag.IssuerOfAccessionNumberSequence, 1).add(issuer);
                zip.putNextEntry(new ZipEntry(entry.getName()));
                // Avoid closing the enclosing archive when the DICOM writer closes.
                var sink = new FilterOutputStream(zip) { @Override public void close() throws IOException { flush(); } };
                try (var dicom = new DicomOutputStream(sink, fmi.getString(Tag.TransferSyntaxUID))) {
                    dicom.writeDataset(fmi, dataset);
                }
                zip.closeEntry();
                count++;
            }
            }
        }
        System.out.println("Created demo archive with " + count + " DICOM instances");
    }
}
