package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.QueueRoomDtos;
import java.util.List;
import java.util.UUID;

public interface QueueStateMachine {
    QueueRoomDtos.Entry callNext(UUID roomId, String stage, AuthenticatedAccount actor);
    QueueRoomDtos.Entry markAbsent(UUID entryId, AuthenticatedAccount actor);
    QueueRoomDtos.Entry returnFromAbsence(UUID entryId, UUID beforeEntryId, String reason, AuthenticatedAccount actor);
    List<QueueRoomDtos.Entry> list(UUID roomId, String stage, AuthenticatedAccount actor);
    List<Integer> nowServing(UUID roomId);
}
