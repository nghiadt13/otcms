package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.util.List;

public record AdminAppointmentSearchPage(
        List<AdminAppointmentSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages) { }
