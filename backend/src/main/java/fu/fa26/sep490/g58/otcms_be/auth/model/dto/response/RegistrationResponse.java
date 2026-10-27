package fu.fa26.sep490.g58.otcms_be.auth.model.dto.response;
import fu.fa26.sep490.g58.otcms_be.auth.exception.*;

import java.util.UUID;

public record RegistrationResponse(UUID accountId, String status, String message) { }
