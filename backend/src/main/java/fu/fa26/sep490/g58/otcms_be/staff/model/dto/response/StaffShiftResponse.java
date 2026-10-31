package fu.fa26.sep490.g58.otcms_be.staff.model.dto.response;

import java.util.List;
import java.util.UUID;

public record StaffShiftResponse(String id, String date, String startTime, String endTime,
        String staffId, String staffName, String role, String roomId, String roomName,
        String roomCategory, List<String> allowedActions) { }
