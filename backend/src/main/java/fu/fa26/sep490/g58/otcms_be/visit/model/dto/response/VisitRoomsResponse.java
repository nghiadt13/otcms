package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.util.List;

public class VisitRoomsResponse {
    private final List<VisitRoomResponse> items;

    public VisitRoomsResponse(List<VisitRoomResponse> items) { this.items = List.copyOf(items); }
    public List<VisitRoomResponse> getItems() { return items; }
}
