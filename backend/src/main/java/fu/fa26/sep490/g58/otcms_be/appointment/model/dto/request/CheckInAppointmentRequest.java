package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;

/** Check-in accepts an empty JSON object; the idempotency key is an HTTP header. */
@Schema(name = "CheckInRequest")
public class CheckInAppointmentRequest extends StrictRequestBody {
}
