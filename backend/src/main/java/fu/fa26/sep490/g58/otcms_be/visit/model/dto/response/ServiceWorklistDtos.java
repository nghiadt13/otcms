package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Worklist of a service room (UC-46): the SERVICE queue with the patient header and the orders for that room. */
public final class ServiceWorklistDtos {
    private ServiceWorklistDtos() { }

    /**
     * @param orderId   for imaging this is also the imaging request id used by the start/upload APIs
     * @param paid      false means the technician must not accept the order (D7, MSG-95)
     * @param status    clinical order status (ORDERED, RESULTS_AVAILABLE, CANCELLED)
     */
    public record Order(UUID orderId, String orderCode, UUID serviceId, String serviceName, String serviceType,
            String status, boolean paid, String clinicalReason, String bodyArea, String instructions,
            String executionStatus) { }

    public record Patient(UUID patientProfileId, String fullName, LocalDate dateOfBirth, Integer age, String gender) { }

    /** One queue row. {@code status} is WAITING, CALLED, IN_EXAMINATION or ABSENT. */
    public record Entry(UUID entryId, UUID visitId, Integer queueNumber, String status, long sortOrder,
            Instant queuedAt, Instant calledAt, Patient patient, List<Order> orders) { }

    public record Worklist(UUID roomId, List<Entry> entries) { }
}
