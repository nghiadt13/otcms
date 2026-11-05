package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import java.util.List;

public record DiagnosticAssignedUploadWorkPage(List<DiagnosticAssignedUploadWorkItem> items,
        int page, int size, long totalItems) {
    public DiagnosticAssignedUploadWorkPage {
        items = List.copyOf(items);
    }
}
