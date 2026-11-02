package fu.fa26.sep490.g58.otcms_be.appointment.service;


import java.util.List;

public class AppointmentSearchPage {
    private final List<AppointmentSummary> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    public AppointmentSearchPage(List<AppointmentSummary> items, int page, int size, long totalElements, int totalPages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }
    public List<AppointmentSummary> getItems() { return items; }
    public List<AppointmentSummary> items() { return items; }
    public int getPage() { return page; }
    public int page() { return page; }
    public int getSize() { return size; }
    public int size() { return size; }
    public long getTotalElements() { return totalElements; }
    public long totalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public int totalPages() { return totalPages; }
}
