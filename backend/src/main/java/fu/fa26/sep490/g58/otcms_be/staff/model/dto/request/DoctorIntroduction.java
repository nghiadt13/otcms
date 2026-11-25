package fu.fa26.sep490.g58.otcms_be.staff.model.dto.request;

import jakarta.validation.constraints.NotNull;

public record DoctorIntroduction(@NotNull String specialties, @NotNull String qualifications,
        @NotNull String biography) { }
