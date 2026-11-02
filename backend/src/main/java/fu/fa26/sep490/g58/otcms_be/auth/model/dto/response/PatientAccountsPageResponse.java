package fu.fa26.sep490.g58.otcms_be.auth.model.dto.response;

import java.util.List;

public record PatientAccountsPageResponse(List<PatientAccountAdminResponse> items, int page, int size,
        long totalItems, int totalPages) { }
