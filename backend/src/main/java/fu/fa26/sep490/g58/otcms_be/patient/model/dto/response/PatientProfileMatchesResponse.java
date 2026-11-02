package fu.fa26.sep490.g58.otcms_be.patient.model.dto.response;

import java.util.List;

public record PatientProfileMatchesResponse(List<PatientProfileSummaryResponse> items) {
    public PatientProfileMatchesResponse { items = List.copyOf(items); }
}
