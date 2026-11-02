package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response;

import java.util.List;

public record AdminAppointmentPageResponse(List<AdminAppointmentResponse> items, int page, int size,
                                           long totalElements, int totalPages) { }
