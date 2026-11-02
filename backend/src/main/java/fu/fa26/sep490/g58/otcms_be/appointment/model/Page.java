package fu.fa26.sep490.g58.otcms_be.appointment.model;

import java.util.List;

public class Page {
    private final List<Row> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    public Page(List<Row> items, int page, int size, long totalElements, int totalPages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }
    public List<Row> getItems() { return items; }
    public List<Row> items() { return items; }
    public int getPage() { return page; }
    public int page() { return page; }
    public int getSize() { return size; }
    public int size() { return size; }
    public long getTotalElements() { return totalElements; }
    public long totalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public int totalPages() { return totalPages; }
}
