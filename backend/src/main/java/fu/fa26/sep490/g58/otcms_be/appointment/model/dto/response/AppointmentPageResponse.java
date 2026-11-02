package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response;


import java.util.List;

public class AppointmentPageResponse {
    private final List<AppointmentResponse> items;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    public AppointmentPageResponse(List<AppointmentResponse> items, int page, int size, long totalElements, int totalPages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }
    public List<AppointmentResponse> getItems() { return items; }
    public List<AppointmentResponse> items() { return items; }
    public int getPage() { return page; }
    public int page() { return page; }
    public int getSize() { return size; }
    public int size() { return size; }
    public long getTotalElements() { return totalElements; }
    public long totalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public int totalPages() { return totalPages; }
}
