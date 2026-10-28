package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Patient identity attested by the Receptionist; idempotency key remains an HTTP header. */
@Schema(name = "CheckInRequest")
public class CheckInAppointmentRequest extends StrictRequestBody {
    @NotNull
    private UUID verifiedPatientProfileId;

    public UUID getVerifiedPatientProfileId() { return verifiedPatientProfileId; }
    public void setVerifiedPatientProfileId(UUID verifiedPatientProfileId) {
        this.verifiedPatientProfileId = verifiedPatientProfileId;
    }
}
