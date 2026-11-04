package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import java.util.List;

public record DiagnosticHistoryPage(List<DiagnosticHistoryItem> items, int page, int size, long totalItems) {
    public DiagnosticHistoryPage {
        items = List.copyOf(items);
    }
}
