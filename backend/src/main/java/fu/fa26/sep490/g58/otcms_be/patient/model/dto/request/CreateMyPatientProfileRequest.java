package fu.fa26.sep490.g58.otcms_be.patient.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Thân POST /api/v1/me/patient-profile: bệnh nhân tự hoàn thành hồ sơ hành chính của chính mình
 * lần đầu. Số điện thoại là của bệnh nhân nên không nhận phoneOwnerType; địa chỉ bắt buộc.
 */
public record CreateMyPatientProfileRequest(@NotBlank String fullName, @NotBlank String phone,
        @NotNull LocalDate dateOfBirth, @NotBlank String gender, @NotBlank String address,
        String nationalId, String identityDocumentType) { }
