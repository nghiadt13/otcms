package fu.fa26.sep490.g58.otcms_be.auth.dto;

import java.util.UUID;

public record RegistrationResponse(UUID accountId, String status, String message) { }
