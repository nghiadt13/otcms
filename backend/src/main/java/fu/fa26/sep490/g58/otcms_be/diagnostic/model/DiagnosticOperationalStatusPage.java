package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import java.util.List;

public record DiagnosticOperationalStatusPage(List<DiagnosticOperationalStatus> items,
        int page, int size, long totalItems) {
    public DiagnosticOperationalStatusPage {
        items = List.copyOf(items);
    }
}
