package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the visit lifecycle endpoints (return, transfers, walk-in suggestions). */
public final class VisitLifecycleDtos {
    private VisitLifecycleDtos() { }

    /** A visit the patient may return to: OPEN, Awaiting Conclusion, 7-day period not ended. */
    public record ReturnCandidate(UUID visitId, Instant firstCheckInAt, LocalDate returnDeadline,
            UUID doctorProfileId, String doctorName, UUID roomId, String roomLabel) { }

    /** Where the visit stands in the exam queue after a return or a doctor transfer. */
    public record VisitQueueTicket(UUID visitId, String status, Integer queueNumber, UUID doctorProfileId,
            String doctorName, UUID roomId, String roomLabel) { }

    /** {@code messageCode} is MSG-43 (room, queue or visit status updated). */
    public record Updated<T>(String messageCode, T data) {
        public static <T> Updated<T> of(T data) { return new Updated<>("MSG-43", data); }
    }

    public record AttendingDoctorTransferRequest(@NotNull UUID doctorProfileId,
            @NotBlank @Size(max = 500) String reason) { }

    public record ServiceOrderRoomTransferRequest(@NotNull UUID newRoomId, @NotBlank @Size(max = 500) String reason) { }

    public record ServiceOrderRoomTransfer(UUID orderId, UUID roomId, UUID previousRoomId) { }

    /** One suggested walk-in room, ordered by the number of people waiting; the first one is the default. */
    public record WalkInSuggestion(UUID roomId, String roomCode, String roomLabel, UUID doctorProfileId,
            String doctorName, long waitingCount) { }

    public record WalkInSuggestions(List<WalkInSuggestion> items) { }
}
