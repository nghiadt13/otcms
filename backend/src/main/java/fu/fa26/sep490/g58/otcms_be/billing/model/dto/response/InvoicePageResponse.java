package fu.fa26.sep490.g58.otcms_be.billing.model.dto.response;

import java.util.List;

public record InvoicePageResponse(int page, int size, boolean hasMore, List<InvoiceResponse> items) { }
