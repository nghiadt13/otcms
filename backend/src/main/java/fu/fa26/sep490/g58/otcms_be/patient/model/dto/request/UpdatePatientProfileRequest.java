package fu.fa26.sep490.g58.otcms_be.patient.model.dto.request;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdatePatientProfileRequest(@NotBlank String fullName, @NotBlank String phone,
        @NotBlank String phoneOwnerType, @NotNull LocalDate dateOfBirth, @NotBlank String gender,
        String address, String nationalId, String identityDocumentType,
        String email, String emailOwnerType, @NotNull @PositiveOrZero Long version) {
    public UpdatePatientProfileRequest(String fullName, String phone, String phoneOwnerType, LocalDate dateOfBirth,
            String gender, String address, String nationalId, String identityDocumentType, Long version) {
        this(fullName, phone, phoneOwnerType, dateOfBirth, gender, address, nationalId, identityDocumentType,
                null, null, version);
    }
}
