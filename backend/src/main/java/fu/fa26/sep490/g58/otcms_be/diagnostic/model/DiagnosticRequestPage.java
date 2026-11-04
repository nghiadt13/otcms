package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import java.util.List;

/** Page data independent of Spring Data and HTTP pagination types. */
public final class DiagnosticRequestPage {
    private final List<DiagnosticRequestView> items;
    private final int page;
    private final int size;
    private final long totalItems;

    public DiagnosticRequestPage(List<DiagnosticRequestView> items, int page, int size, long totalItems) {
        this.items = List.copyOf(items);
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
    }

    public List<DiagnosticRequestView> items() { return items; }
    public int page() { return page; }
    public int size() { return size; }
    public long totalItems() { return totalItems; }
}
