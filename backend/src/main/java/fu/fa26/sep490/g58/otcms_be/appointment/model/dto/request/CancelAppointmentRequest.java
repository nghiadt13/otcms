package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request;

import jakarta.validation.constraints.Size;
import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import tools.jackson.databind.JsonNode;

public class CancelAppointmentRequest extends StrictRequestBody {
    @Size(max = 1002) private String reason;
    /** Staff only: true when the clinic is at fault (doctor absent, nobody to replace) so a paid fee awaits a refund. */
    private boolean clinicFault;

    public String getReason() { return reason; }
    public void setReason(JsonNode reason) { this.reason = strictText(reason); }
    public boolean isClinicFault() { return clinicFault; }
    public void setClinicFault(JsonNode value) {
        if (value == null || value.isNull()) { clinicFault = false; return; }
        if (!value.isBoolean()) throw new IllegalArgumentException("Expected boolean value");
        clinicFault = value.booleanValue();
    }
}
