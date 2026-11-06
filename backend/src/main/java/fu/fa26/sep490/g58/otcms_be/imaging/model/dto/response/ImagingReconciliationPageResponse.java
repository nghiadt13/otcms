package fu.fa26.sep490.g58.otcms_be.imaging.model.dto.response;

import java.util.List;

public record ImagingReconciliationPageResponse(List<ImagingReconciliationCaseResponse> items,
        int page, int size, long totalElements, int totalPages) { }
