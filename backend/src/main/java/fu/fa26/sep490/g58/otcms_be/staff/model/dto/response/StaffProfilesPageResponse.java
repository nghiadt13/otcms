package fu.fa26.sep490.g58.otcms_be.staff.model.dto.response;

import java.util.List;

public record StaffProfilesPageResponse(List<StaffProfileResponse> items, int page, int size,
        long totalItems, int totalPages) { }
