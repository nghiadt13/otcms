package fu.fa26.sep490.g58.otcms_be.visit.model;

import java.util.UUID;

/** Queue number granted to a visit, with the doctor and room it is queued under (shown after check-in). */
public record ExamQueueTicket(int queueNumber, UUID doctorProfileId, String doctorName, UUID roomId,
        String roomLabel) {
}
