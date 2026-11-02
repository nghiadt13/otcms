package fu.fa26.sep490.g58.otcms_be.staff.model.dto.response;

import java.util.List;

public class DoctorItems {
    private final List<DoctorPublicResponse> items;
    public DoctorItems(List<DoctorPublicResponse> items) {
        this.items = items;
    }
    public List<DoctorPublicResponse> getItems() { return items; }
    public List<DoctorPublicResponse> items() { return items; }
}
