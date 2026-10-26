package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.WalkInReceptionQueueEntryResponse;

/** Visit-owned persistence contract used by walk-in reception workflows. */
public interface WalkInReceptionQueueService {
    WalkInReceptionQueueEntryResponse createWaitingEntry(CreateWalkInReceptionQueueEntryCommand command);
}
