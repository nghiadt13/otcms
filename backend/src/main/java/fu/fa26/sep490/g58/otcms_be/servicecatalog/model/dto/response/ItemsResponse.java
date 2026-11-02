package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response;

import java.util.List;

public class ItemsResponse {
    private final List<ServiceResponse> items;
    public ItemsResponse(List<ServiceResponse> items) {
        this.items = items;
    }
    public List<ServiceResponse> getItems() { return items; }
    public List<ServiceResponse> items() { return items; }
}
