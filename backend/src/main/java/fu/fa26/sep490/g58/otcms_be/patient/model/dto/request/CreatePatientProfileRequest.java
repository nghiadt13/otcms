package fu.fa26.sep490.g58.otcms_be.patient.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class CreatePatientProfileRequest {
    private @NotBlank String fullName;
    private @NotBlank String phone;
    private @NotBlank String phoneOwnerType;
    private @NotNull LocalDate dateOfBirth;
    private @NotBlank String gender;
    public CreatePatientProfileRequest() { }
    public CreatePatientProfileRequest(@NotBlank String fullName, @NotBlank String phone, @NotBlank String phoneOwnerType, @NotNull LocalDate dateOfBirth, @NotBlank String gender) {
        this.fullName = fullName;
        this.phone = phone;
        this.phoneOwnerType = phoneOwnerType;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
    }
    public String getFullName() { return fullName; }
    public String fullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public String phone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPhoneOwnerType() { return phoneOwnerType; }
    public String phoneOwnerType() { return phoneOwnerType; }
    public void setPhoneOwnerType(String phoneOwnerType) { this.phoneOwnerType = phoneOwnerType; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public LocalDate dateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public String gender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
}
