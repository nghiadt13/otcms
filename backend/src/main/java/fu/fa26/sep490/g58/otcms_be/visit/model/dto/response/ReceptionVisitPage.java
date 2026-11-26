package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record ReceptionVisitPage(int page, boolean hasMore, List<Item> items) {
    public record Item(UUID visitId, UUID patientId, String patientName, String status, Instant arrivedAt,
            String patientCode) { }
}
