package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clamav;

public interface ClamAvScanner {
    ScanResult scan(byte[] content);

    enum ScanResult { CLEAN, INFECTED, UNAVAILABLE }
}
