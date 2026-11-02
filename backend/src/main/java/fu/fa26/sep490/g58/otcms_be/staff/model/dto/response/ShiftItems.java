package fu.fa26.sep490.g58.otcms_be.staff.model.dto.response;

import java.util.List;

public class ShiftItems {
    private final List<ShiftResponse> items;
    public ShiftItems(List<ShiftResponse> items) {
        this.items = items;
    }
    public List<ShiftResponse> getItems() { return items; }
    public List<ShiftResponse> items() { return items; }
}
