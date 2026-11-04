package fu.fa26.sep490.g58.otcms_be.patient.model.dto;

import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.Relationship;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileDetailResponse;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

/** Explicit administrative API projections; no account entity or clinical fields. */
public final class DependentDtos {
    private DependentDtos() { }
    public record AddDependentRequest(@NotBlank String fullName, @NotNull LocalDate dateOfBirth,
            @NotBlank String gender, String nationalId, String identityDocumentType,
            @NotNull Relationship relationship) { }
    public record UpdateDependentRequest(@NotBlank String fullName, @NotNull LocalDate dateOfBirth,
            @NotBlank String gender, String phone, String address, String nationalId, String identityDocumentType) { }
    /** Result of requesting the add-dependent OTP: the request to verify and the masked recipient email. */
    public record AddOtpResult(UUID requestId, String maskedEmail) { }
    public record VerifyOtpRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String code) { }
    public record CounterConfirmationRequest(@NotNull @AssertTrue Boolean identityChecked) { }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record LinkResult(String result, UUID linkId, String maskedEmail, PatientProfileDetailResponse profile) { }
    public record Items<T>(List<T> items) { }
    public record ProfileItem(UUID patientProfileId, String fullName, LocalDate dateOfBirth, String gender,
            String relationship, String linkStatus, UUID linkId) { }
    public record CounterItem(UUID linkId, UUID patientProfileId, String fullName, LocalDate dateOfBirth,
            String gender, String nationalId, String identityDocumentType, Relationship relationship, Instant requestedAt) { }
}
